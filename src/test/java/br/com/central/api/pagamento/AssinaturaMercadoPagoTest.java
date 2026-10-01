package br.com.central.api.pagamento;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class AssinaturaMercadoPagoTest {
    @Test void validaManifestoERejeitaMudancaDeIdOuRequest() throws Exception {
        var mac=javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec("segredo-de-teste".getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"));
        String assinatura="ts=1704908010,v1="+java.util.HexFormat.of().formatHex(mac.doFinal("id:123;request-id:req-1;ts:1704908010;".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThat(AssinaturaMercadoPago.valida(assinatura,"req-1","123","segredo-de-teste")).isTrue();
        assertThat(AssinaturaMercadoPago.valida(assinatura,"req-1","124","segredo-de-teste")).isFalse();
        assertThat(AssinaturaMercadoPago.valida(assinatura,"req-2","123","segredo-de-teste")).isFalse();
        assertThat(AssinaturaMercadoPago.valida(assinatura+",ts=2","req-1","123","segredo-de-teste")).isFalse();
    }
    @Test void entradasIncompletasNaoSaoAceitas() {
        assertThat(AssinaturaMercadoPago.valida(null,"req-1","123","teste")).isFalse();
        assertThat(AssinaturaMercadoPago.valida("ts=1,v1=x","req-1","123","teste")).isFalse();
        assertThat(AssinaturaMercadoPago.valida("ts=1,v1=x","req-1","../x","teste")).isFalse();
    }
}
