package br.com.central.api.pagamento;

import br.com.central.api.comercial.*;
import br.com.central.api.web.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;

/** Valor e vínculo congelados no servidor; reserva curta, criação HTTP fora da transação. */
@Service
public class CheckoutMercadoPagoService {
    private final JdbcTemplate jdbc;
    private final CobrancaRepository cobrancas;
    private final ContratacaoRepository contratos;
    private final MercadoPagoHttp mp;
    private final TransactionTemplate tx;
    public CheckoutMercadoPagoService(JdbcTemplate jdbc,CobrancaRepository cobrancas,ContratacaoRepository contratos,MercadoPagoHttp mp,PlatformTransactionManager tm) {
        this.jdbc=jdbc;this.cobrancas=cobrancas;this.contratos=contratos;this.mp=mp;this.tx=new TransactionTemplate(tm);
    }
    public record Link(UUID tentativaId,String url,String situacao) {}
    private record Reserva(UUID dono,BigDecimal valor,Link existente) {}
    public Link criar(UUID cobrancaId,UUID tentativa) {
        if (!mp.configurado()) throw new BadRequestException("Checkout Mercado Pago ainda não configurado.");
        Reserva r=tx.execute(status -> reservar(cobrancaId,tentativa));
        if (r.existente()!=null) return r.existente();
        try {
            var checkout=mp.criarCheckout(tentativa,r.valor());
            return tx.execute(status -> {
                int alterados=jdbc.update("update checkout_mercadopago set status='PRONTO',preferencia_id=?,url=?,reservado_por=null,reserva_ate=null where id=? and reservado_por=? and reserva_ate>now()",
                    checkout.id(),checkout.url(),tentativa,r.dono());
                if (alterados!=1) throw new ConflictException("Checkout em processamento; tente novamente.","CHECKOUT_CONFLITO");
                return new Link(tentativa,checkout.url(),"PRONTO");
            });
        } catch (RuntimeException ex) {
            tx.executeWithoutResult(status -> jdbc.update("update checkout_mercadopago set status='ERRO',reservado_por=null,reserva_ate=null where id=? and reservado_por=?",tentativa,r.dono()));
            throw new BadRequestException("Não foi possível criar checkout. Tente novamente com a mesma tentativa.");
        }
    }
    private Reserva reservar(UUID cobrancaId,UUID tentativa) {
        UUID contrato=cobrancas.findById(cobrancaId).map(Cobranca::getContratacaoId).orElseThrow(() -> new ResourceNotFoundException("Cobrança não encontrada."));
        var c=contratos.buscarParaAlterar(contrato).orElseThrow();
        // Refresh obrigatório: não usar o snapshot carregado antes da trava da contratação.
        var valores=jdbc.queryForList("select status,valor from cobranca where id=? for update",cobrancaId);
        var cobranca=valores.getFirst();BigDecimal valor=(BigDecimal)cobranca.get("valor");
        if (c.getSituacaoComercial()==SituacaoComercial.CANCELADA || !"ABERTA".equals(cobranca.get("status")) || valor.signum()<=0)
            throw new BadRequestException("Somente cobrança aberta e positiva recebe checkout.");
        jdbc.update("insert into checkout_mercadopago(id,cobranca_id,valor) values(?,?,?) on conflict(id) do nothing",tentativa,cobrancaId,valor);
        var row=jdbc.queryForMap("select *,reserva_ate>now() as ocupada from checkout_mercadopago where id=? for update",tentativa);
        if (!cobrancaId.equals(row.get("cobranca_id"))) throw new ConflictException("Tentativa pertence a outra cobrança.","CHECKOUT_CONFLITO");
        if (valor.compareTo((BigDecimal)row.get("valor"))!=0) throw new ConflictException("Valor da cobrança mudou. Gere uma nova tentativa de checkout.","CHECKOUT_CONFLITO");
        if ("PRONTO".equals(row.get("status"))) return new Reserva(null,valor,new Link(tentativa,(String)row.get("url"),"PRONTO"));
        if (Boolean.TRUE.equals(row.get("ocupada"))) throw new ConflictException("Checkout em processamento; aguarde.","CHECKOUT_CONFLITO");
        UUID dono=UUID.randomUUID();
        jdbc.update("update checkout_mercadopago set status='CRIANDO',reservado_por=?,reserva_ate=now()+interval '120 seconds' where id=?",dono,tentativa);
        return new Reserva(dono,valor,null);
    }
}
