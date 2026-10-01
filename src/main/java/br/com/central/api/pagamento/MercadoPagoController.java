package br.com.central.api.pagamento;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.UUID;

@RestController
@RequestMapping("/cobrancas")
@PreAuthorize("hasAuthority('ROLE_OPERADOR')")
public class MercadoPagoController {
    private final CheckoutMercadoPagoService checkout;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    public MercadoPagoController(CheckoutMercadoPagoService checkout,org.springframework.jdbc.core.JdbcTemplate jdbc) {this.checkout=checkout;this.jdbc=jdbc;}
    @PostMapping("/{id}/checkout")
    public CheckoutMercadoPagoService.Link criar(@PathVariable UUID id,@RequestHeader("Idempotency-Key") UUID tentativa) {
        return checkout.criar(id,tentativa);
    }

    public record PagamentoLinha(String pagamentoId,String situacao,String statusProvedor,boolean aplicado) {}
    @GetMapping("/{id}/pagamentos-online")
    public java.util.List<PagamentoLinha> pagamentos(@PathVariable UUID id) {
        return jdbc.query("""
            select p.id,p.situacao,p.status_provedor,p.aplicado from pagamento_mercadopago p
              join checkout_mercadopago c on c.id=p.checkout_id where c.cobranca_id=?
              order by p.recebido_em desc,p.id limit 100
            """,(rs,n) -> new PagamentoLinha(rs.getString(1),rs.getString(2),rs.getString(3),rs.getBoolean(4)),id);
    }
    @PostMapping("/pagamentos-online/{id}/reconciliar")
    public void reconciliar(@PathVariable String id) {
        if (!id.matches("[0-9]{1,30}")) throw new br.com.central.api.web.BadRequestException("Pagamento inválido.");
        jdbc.update("update pagamento_mercadopago set situacao='PENDENTE',revisao=revisao+1,proxima_tentativa=now(),tentativas=0 where id=?",id);
    }
}
