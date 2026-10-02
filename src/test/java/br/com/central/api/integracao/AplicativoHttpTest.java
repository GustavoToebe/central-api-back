package br.com.central.api.integracao;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AplicativoHttpTest {

    private static final byte[] SEGREDO =
            "segredo-de-teste-nao-usar-em-producao-0123456789".getBytes(StandardCharsets.UTF_8);

    @Test
    void postBateOVetorDoContrato() {
        byte[] corpo = "{\"contratacaoId\":\"0f8e2c1a-1111-4a2b-9c3d-000000000001\"}"
                .getBytes(StandardCharsets.UTF_8);
        AplicativoHttp http = cliente(1790000000L, "5b1f2a0e-8c3d-4f6a-9b7e-1d2c3b4a5f60");
        MockRestServiceServer servidor = servidorDe(http);
        servidor.expect(requestTo("http://app.test/integracao/v1/instancias"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(IntegracaoFiltro.CHAVE, "teste-central"))
                .andExpect(header(IntegracaoFiltro.TIMESTAMP, "1790000000"))
                .andExpect(header(IntegracaoFiltro.NONCE, "5b1f2a0e-8c3d-4f6a-9b7e-1d2c3b4a5f60"))
                .andExpect(header(IntegracaoFiltro.ASSINATURA,
                        "v1=4c63e60805c925d7c3cd321c8726e984ff1202ba6e62df81c6f5749694f99f32"))
                .andExpect(header("Idempotency-Key", "0f8e2c1a-1111-4a2b-9c3d-000000000001"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        http.enviar("http://app.test", "POST", "/integracao/v1/instancias", corpo,
                "0f8e2c1a-1111-4a2b-9c3d-000000000001");
        servidor.verify();
    }

    @Test
    void getBateOVetorDoContrato() {
        AplicativoHttp http = cliente(1790000300L, "a7c9e1f2-2222-4b3c-8d4e-000000000002");
        MockRestServiceServer servidor = servidorDe(http);
        servidor.expect(requestTo("http://app.test/integracao/v1/produtos/SERVIRE/direitos"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(IntegracaoFiltro.ASSINATURA,
                        "v1=976359d02a3d078c41896cff976ba58fcb6cad78060392fbc43b676a95f8b113"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        http.enviar("http://app.test", "GET", "/integracao/v1/produtos/SERVIRE/direitos", new byte[0], null);
        servidor.verify();
    }

    @Test
    void respostaMaiorQueUmMiBInterrompeLeitura() {
        AplicativoHttp http=cliente(1790000300L,"nonce-limite");
        MockRestServiceServer servidor=servidorDe(http);
        servidor.expect(requestTo("http://app.test/integracao/v1/recursos"))
           .andRespond(withSuccess("x".repeat(1048577),MediaType.APPLICATION_JSON));
        org.assertj.core.api.Assertions.assertThatThrownBy(()->http.enviar("http://app.test","GET","/integracao/v1/recursos",null,null))
           .isInstanceOf(IllegalStateException.class);
        servidor.verify();
    }

    private final MockRestServiceServer[] ultimo = new MockRestServiceServer[1];

    private AplicativoHttp cliente(long epoch, String nonce) {
        RestClient.Builder builder = RestClient.builder();
        ultimo[0] = MockRestServiceServer.bindTo(builder).build();
        return new AplicativoHttp(
                builder.build(),
                "teste-central",
                SEGREDO,
                Clock.fixed(Instant.ofEpochSecond(epoch), ZoneOffset.UTC),
                () -> nonce);
    }

    private MockRestServiceServer servidorDe(AplicativoHttp ignorado) {
        return ultimo[0];
    }
}
