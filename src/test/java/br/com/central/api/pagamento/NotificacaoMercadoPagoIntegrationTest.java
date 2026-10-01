package br.com.central.api.pagamento;

import br.com.central.api.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@TestPropertySource(properties="central.mercadopago.webhook-secret=notificacao-test")
class NotificacaoMercadoPagoIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private String assinatura(String id) throws Exception {
        var mac=javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec("notificacao-test".getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"));
        return "ts=1704908010,v1="+java.util.HexFormat.of().formatHex(mac.doFinal(("id:"+id+";request-id:req-test;ts:1704908010;").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    @Test void notificacaoPublicaAssinadaPersisteAntesDe200ERepeticaoReativaSemDuplicar() throws Exception {
        String id=Long.toUnsignedString(UUID.randomUUID().getMostSignificantBits());
        for(int n=0;n<2;n++) mvc.perform(post("/webhooks/mercadopago").param("data.id",id).param("type","payment")
            .header("x-signature",assinatura(id)).header("x-request-id","req-test")
            .contentType("application/json").content("{\"status\":\"approved\",\"data\":{\"id\":\"outro-id\"}}"))
            .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from pagamento_mercadopago where id=?",Integer.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select revisao from pagamento_mercadopago where id=?",Long.class,id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select aplicado from pagamento_mercadopago where id=?",Boolean.class,id)).isFalse();
    }
    @Test void assinaturaForjadaOuAusenteNaoPersisteNada() throws Exception {
        String id=Long.toUnsignedString(UUID.randomUUID().getMostSignificantBits());
        mvc.perform(post("/webhooks/mercadopago").param("data.id",id).param("type","payment")).andExpect(status().isUnauthorized());
        mvc.perform(post("/webhooks/mercadopago").param("data.id",id).param("type","payment")
            .header("x-signature",assinatura("123")).header("x-request-id","req-test")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from pagamento_mercadopago where id=?",Integer.class,id)).isZero();
    }
    @Test void checkoutExigeAutenticacaoMesmoComIdempotencyKey() throws Exception {
        mvc.perform(post("/cobrancas/"+UUID.randomUUID()+"/checkout").header("Idempotency-Key",UUID.randomUUID().toString()))
            .andExpect(status().isUnauthorized());
    }
}
