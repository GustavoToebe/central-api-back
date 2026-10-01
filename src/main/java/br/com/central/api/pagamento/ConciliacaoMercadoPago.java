package br.com.central.api.pagamento;

import br.com.central.api.comercial.*;
import br.com.central.api.comercial.dto.FinanceiroDtos.RegistrarPagamentoRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;

/** Caixa durável: consulta fonte oficial fora da transação; baixa exata e idempotente no banco. */
@Component
public class ConciliacaoMercadoPago {
    private final JdbcTemplate jdbc;
    private final MercadoPagoHttp mp;
    private final BillingService billing;
    private final ContratacaoRepository contratos;
    private final TransactionTemplate tx;
    private final String coletor;
    private final boolean producao;
    public ConciliacaoMercadoPago(JdbcTemplate jdbc,MercadoPagoHttp mp,BillingService billing,ContratacaoRepository contratos,
        PlatformTransactionManager tm,@Value("${central.mercadopago.collector-id:}") String coletor,
        @Value("${central.mercadopago.producao:false}") boolean producao) {
        this.jdbc=jdbc;this.mp=mp;this.billing=billing;this.contratos=contratos;this.tx=new TransactionTemplate(tm);
        this.coletor=coletor;this.producao=producao;
    }
    private record Reserva(String id,long revisao,UUID dono) {}
    @Scheduled(fixedDelay=5000,initialDelay=20000)
    public void processar() {
        if (!mp.configurado() || coletor.isBlank()) return;
        Reserva reserva=tx.execute(status -> reservar());
        if (reserva==null) return;
        try {
            var pagamento=mp.consultarPagamento(reserva.id());
            tx.executeWithoutResult(status -> concluir(reserva,pagamento));
        } catch (RuntimeException ex) {
            tx.executeWithoutResult(status -> jdbc.update("""
                update pagamento_mercadopago set reservado_por=null,reserva_ate=null,tentativas=tentativas+1,
                proxima_tentativa=now()+interval '5 minutes',situacao=case when tentativas>=11 then 'REVISAR' else 'PENDENTE' end
                where id=? and reservado_por=?
                """,reserva.id(),reserva.dono()));
        }
    }
    private Reserva reservar() {
        var ids=jdbc.queryForList("""
            select id,revisao from pagamento_mercadopago where situacao='PENDENTE' and proxima_tentativa<=now()
              and (reserva_ate is null or reserva_ate<=now()) order by proxima_tentativa,id limit 1 for update skip locked
            """);
        if (ids.isEmpty()) return null;
        var r=ids.getFirst();UUID dono=UUID.randomUUID();String id=(String)r.get("id");
        jdbc.update("update pagamento_mercadopago set reservado_por=?,reserva_ate=now()+interval '120 seconds' where id=?",dono,id);
        return new Reserva(id,((Number)r.get("revisao")).longValue(),dono);
    }
    private void concluir(Reserva reserva,MercadoPagoHttp.Pagamento p) {
        var rows=jdbc.queryForList("select *,reserva_ate>now() as valida from pagamento_mercadopago where id=? for update",reserva.id());
        if (rows.isEmpty()) return;var row=rows.getFirst();
        if (!reserva.dono().equals(row.get("reservado_por")) || !Boolean.TRUE.equals(row.get("valida"))) return;
        if (((Number)row.get("revisao")).longValue()!=reserva.revisao()) {
            jdbc.update("update pagamento_mercadopago set reservado_por=null,reserva_ate=null where id=?",reserva.id());return;
        }
        String situacao="CONCILIADO";UUID checkout=null;
        try { checkout=UUID.fromString(p.referencia()); } catch (RuntimeException ex) { situacao="REVISAR"; }
        var tentativas=checkout==null ? List.<Map<String,Object>>of() : jdbc.queryForList("select * from checkout_mercadopago where id=?",checkout);
        if (!coletor.equals(p.coletor()) || p.producao()!=producao || !"BRL".equals(p.moeda()) || tentativas.isEmpty()) situacao="REVISAR";
        boolean aplicado=Boolean.TRUE.equals(row.get("aplicado"));
        if (!tentativas.isEmpty() && "CONCILIADO".equals(situacao)) {
            var tentativa=tentativas.getFirst();UUID cobrancaId=(UUID)tentativa.get("cobranca_id");
            UUID contrato=jdbc.queryForObject("select contratacao_id from cobranca where id=?",UUID.class,cobrancaId);
            var contratacao=contratos.buscarParaAlterar(contrato).orElseThrow();
            var cobranca=jdbc.queryForMap("select status,valor from cobranca where id=? for update",cobrancaId);
            if (((BigDecimal)tentativa.get("valor")).compareTo(p.valor())!=0 || ((BigDecimal)cobranca.get("valor")).compareTo(p.valor())!=0) situacao="REVISAR";
            else if ("approved".equals(p.status()) && !aplicado) {
                if (!"ABERTA".equals(cobranca.get("status")) || contratacao.getSituacaoComercial()==SituacaoComercial.CANCELADA) situacao="REVISAR";
                else {
                    LocalDate data=OffsetDateTime.parse(p.aprovadoEm()).atZoneSameInstant(ZoneId.of("America/Sao_Paulo")).toLocalDate();
                    billing.registrarPagamento(contrato,new RegistrarPagamentoRequest(List.of(cobrancaId),data,forma(p.tipo()),p.valor(),"Mercado Pago: "+p.id()));
                    aplicado=true;
                }
            } else if (aplicado && !"approved".equals(p.status())) situacao="REVISAR";
        }
        if ("CONCILIADO".equals(situacao) && Set.of("pending","in_process","authorized").contains(p.status())) situacao="PENDENTE";
        jdbc.update("""
            update pagamento_mercadopago set situacao=?,status_provedor=?,checkout_id=?,aplicado=?,atualizado_em=now(),
                reservado_por=null,reserva_ate=null,proxima_tentativa=now()+interval '15 minutes' where id=?
            """,situacao,p.status(),tentativas.isEmpty()?null:checkout,aplicado,reserva.id());
    }
    private static FormaPagamento forma(String tipo) {
        return switch(tipo) {case "credit_card" -> FormaPagamento.CARTAO_CREDITO;case "debit_card" -> FormaPagamento.CARTAO_DEBITO;
            case "bank_transfer" -> FormaPagamento.PIX;case "ticket" -> FormaPagamento.BOLETO;default -> FormaPagamento.OUTRO;};
    }
}
