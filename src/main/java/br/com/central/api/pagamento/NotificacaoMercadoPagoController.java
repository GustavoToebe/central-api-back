package br.com.central.api.pagamento;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

/** Confere a assinatura antes de persistir. Não lê nem confia no status enviado pelo navegador/body. */
@RestController
public class NotificacaoMercadoPagoController {
    private final JdbcTemplate jdbc;
    private final String segredo;
    public NotificacaoMercadoPagoController(JdbcTemplate jdbc,@Value("${central.mercadopago.webhook-secret:}") String segredo) { this.jdbc=jdbc;this.segredo=segredo; }
    @PostMapping("/webhooks/mercadopago")
    public ResponseEntity<Void> receber(@RequestHeader(value="x-signature",required=false) String assinatura,
        @RequestHeader(value="x-request-id",required=false) String requestId,
        @RequestParam(value="data.id",required=false) String id,
        @RequestParam(value="type",required=false) String tipo) {
        if (!AssinaturaMercadoPago.valida(assinatura,requestId,id,segredo)) return ResponseEntity.status(401).build();
        if (!"payment".equals(tipo)) return ResponseEntity.badRequest().build();
        jdbc.update("""
            insert into pagamento_mercadopago(id) values(?) on conflict(id) do update
            set revisao=pagamento_mercadopago.revisao+1,recebido_em=now(),situacao='PENDENTE',proxima_tentativa=now(),tentativas=0
            """,id);
        return ResponseEntity.ok().build();
    }
}
