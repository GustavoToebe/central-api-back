package br.com.central.api.pagamento;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MercadoPagoHttpTest {
    @Test void checkoutUsaValorDoServidorEReferenciaDaTentativa() {
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();UUID id=UUID.randomUUID();
        server.expect(requestTo("https://api.mercadopago.com/checkout/preferences"))
            .andExpect(header("Authorization","Bearer teste"))
            .andExpect(jsonPath("$.external_reference").value(id.toString()))
            .andExpect(jsonPath("$.items[0].unit_price").value(99.90))
            .andExpect(jsonPath("$.notification_url").value("https://api.exemplo.com/webhooks/mercadopago"))
            .andRespond(withSuccess("{\"id\":\"pref-1\",\"init_point\":\"https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=pref-1\"}",MediaType.APPLICATION_JSON));
        var cliente=new MercadoPagoHttp(builder.build(),"teste","https://painel.exemplo.com/cobrancas","https://api.exemplo.com/webhooks/mercadopago");
        assertThat(cliente.criarCheckout(id,new BigDecimal("99.90")).id()).isEqualTo("pref-1");server.verify();
    }
    @Test void respostaNaoPodeRedirecionarParaHostArbitrario() {
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.mercadopago.com/checkout/preferences"))
            .andRespond(withSuccess("{\"id\":\"pref-1\",\"init_point\":\"https://fraude.test/checkout\"}",MediaType.APPLICATION_JSON));
        var cliente=new MercadoPagoHttp(builder.build(),"teste","https://painel.exemplo.com/cobrancas","https://api.exemplo.com/webhooks/mercadopago");
        assertThatThrownBy(() -> cliente.criarCheckout(UUID.randomUUID(),new BigDecimal("10.00"))).isInstanceOf(IllegalStateException.class);
    }
    @Test void pagamentoConsultadoTemQueTerIdentificadorSolicitado() {
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.mercadopago.com/v1/payments/123"))
            .andRespond(withSuccess("{\"id\":124}",MediaType.APPLICATION_JSON));
        var cliente=new MercadoPagoHttp(builder.build(),"teste","https://painel.exemplo.com/cobrancas","https://api.exemplo.com/webhooks/mercadopago");
        assertThatThrownBy(() -> cliente.consultarPagamento("123")).isInstanceOf(IllegalStateException.class);
    }
    @Test void segredoAusenteNaoDisparaRede() {
        var cliente=new MercadoPagoHttp(RestClient.builder().build(),"","https://painel.exemplo.com","https://api.exemplo.com");
        assertThat(cliente.configurado()).isFalse();
        assertThatThrownBy(() -> cliente.consultarPagamento("123")).isInstanceOf(IllegalStateException.class);
    }
}
