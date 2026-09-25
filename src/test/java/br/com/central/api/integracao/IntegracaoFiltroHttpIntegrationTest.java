package br.com.central.api.integracao;

import br.com.central.api.AbstractIntegrationTest;
import br.com.central.api.operador.Operador;
import br.com.central.api.operador.OperadorRepository;
import br.com.central.api.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class IntegracaoFiltroHttpIntegrationTest extends AbstractIntegrationTest {

    private static final byte[] SEGREDO =
            "segredo-de-teste-nao-usar-em-producao-0123456789".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private OperadorRepository operadorRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Test
    void chaveDesconhecidaRecebe401() throws Exception {
        mockMvc.perform(get("/integracao/v1/saude").header(IntegracaoFiltro.CHAVE, "nao-existe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CHAVE_DESCONHECIDA"));
    }

    @Test
    void horarioVelhoRecebe401() throws Exception {
        assinado("/integracao/v1/saude", "1000", UUID.randomUUID().toString(), "v1=irrelevante")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("TIMESTAMP_FORA_DA_JANELA"));
    }

    @Test
    void assinaturaErradaRecebe401() throws Exception {
        String agora = Long.toString(Instant.now().getEpochSecond());
        assinado("/integracao/v1/saude", agora, UUID.randomUUID().toString(), "v1=0000")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("ASSINATURA_INVALIDA"));
    }

    @Test
    void nonceRepetidoRecebe401EPedidoValidoPassa() throws Exception {
        String agora = Long.toString(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String assinatura = HmacAssinatura.assinar(SEGREDO, "GET", "/integracao/v1/saude", agora, nonce, new byte[0]);

        assinado("/integracao/v1/saude", agora, nonce, assinatura).andExpect(status().isNoContent());
        assinado("/integracao/v1/saude", agora, nonce, assinatura)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NONCE_REPETIDO"));
    }

    @Test
    void caminhoCodificadoRecebe401() throws Exception {
        mockMvc.perform(get(URI.create("/%69ntegracao/v1/saude")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void operadorSemHmacRecebe401() throws Exception {
        Operador operador = operadorRepository.saveAndFlush(new Operador(
                "Op", "filtro-" + UUID.randomUUID() + "@central.test", passwordEncoder.encode("x")));
        String token = jwtService.gerarAccessToken(operador.getId());
        mockMvc.perform(get("/integracao/v1/saude").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CHAVE_DESCONHECIDA"));
    }

    private org.springframework.test.web.servlet.ResultActions assinado(String caminho, String timestamp, String nonce, String assinatura)
            throws Exception {
        return mockMvc.perform(get(caminho)
                .header(IntegracaoFiltro.CHAVE, "teste-servire")
                .header(IntegracaoFiltro.TIMESTAMP, timestamp)
                .header(IntegracaoFiltro.NONCE, nonce)
                .header(IntegracaoFiltro.ASSINATURA, assinatura));
    }
}
