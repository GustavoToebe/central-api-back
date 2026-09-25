package br.com.central.api.comercial;

import br.com.central.api.AbstractIntegrationTest;
import br.com.central.api.operador.Operador;
import br.com.central.api.operador.OperadorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ComercialHttpIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OperadorRepository operadorRepository;
    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private String email;

    @BeforeEach
    void criarOperador() {
        email = "op-" + UUID.randomUUID() + "@central.test";
        operadorRepository.saveAndFlush(new Operador("Ana Operadora", email, passwordEncoder.encode("senha-correta-123")));
    }

    static Stream<String> leituras() {
        UUID id = UUID.randomUUID();
        return Stream.of(
                "/clientes", "/clientes/" + id,
                "/produtos", "/recursos", "/planos", "/adicionais",
                "/contratacoes", "/contratacoes/" + id, "/contratacoes/" + id + "/financeiro");
    }

    static Stream<String> escritas() {
        UUID id = UUID.randomUUID();
        return Stream.of(
                "/clientes", "/produtos", "/recursos", "/planos", "/planos/" + id + "/precos",
                "/adicionais", "/contratacoes",
                "/contratacoes/" + id + "/bloquear",
                "/contratacoes/" + id + "/desbloquear",
                "/contratacoes/" + id + "/cancelar",
                "/contratacoes/" + id + "/tentar-provisionamento",
                "/contratacoes/" + id + "/suporte",
                "/contratacoes/" + id + "/pagamentos",
                "/contratacoes/" + id + "/cobrancas/adiantadas",
                "/contratacoes/" + id + "/cobrancas/" + id + "/estornar",
                "/contratacoes/" + id + "/cobrancas/" + id + "/isentar");
    }

    static Stream<String> atualizacoes() {
        UUID id = UUID.randomUUID();
        return Stream.of(
                "/clientes/" + id, "/produtos/" + id, "/recursos/" + id, "/planos/" + id, "/adicionais/" + id,
                "/contratacoes/" + id, "/contratacoes/" + id + "/plano", "/contratacoes/" + id + "/adicionais");
    }

    @ParameterizedTest
    @MethodSource("leituras")
    void leituraSemTokenRecebe401(String caminho) throws Exception {
        mockMvc.perform(get(caminho)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @MethodSource("escritas")
    void escritaSemTokenRecebe401(String caminho) throws Exception {
        mockMvc.perform(post(caminho).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @MethodSource("atualizacoes")
    void atualizacaoSemTokenRecebe401(String caminho) throws Exception {
        mockMvc.perform(put(caminho).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @MethodSource("leituras")
    void leituraSemPapelDeOperadorRecebe403(String caminho) throws Exception {
        mockMvc.perform(get(caminho).with(authentication(principalSemPapel())))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("escritas")
    void escritaSemPapelDeOperadorRecebe403(String caminho) throws Exception {
        mockMvc.perform(post(caminho).contentType(MediaType.APPLICATION_JSON).content(corpoValido(caminho))
                        .with(authentication(principalSemPapel())))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("atualizacoes")
    void atualizacaoSemPapelDeOperadorRecebe403(String caminho) throws Exception {
        mockMvc.perform(put(caminho).contentType(MediaType.APPLICATION_JSON).content(corpoValido(caminho))
                        .with(authentication(principalSemPapel())))
                .andExpect(status().isForbidden());
    }

    @Test
    void operadorUsaCadastrosContratacaoEFinanceiro() throws Exception {
        String token = token();
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        String hoje = LocalDate.now().toString();

        String clienteId = id(autorizado(token, post("/clientes"),
                "{\"tipo\":\"PF\",\"documento\":\"" + sufixo + "\",\"nome\":\"Cliente " + sufixo
                        + "\",\"contatos\":[{\"nome\":\"Ana\",\"principal\":true}]}", 201));
        autorizado(token, get("/clientes/" + clienteId), null, 200);
        autorizado(token, put("/clientes/" + clienteId),
                "{\"tipo\":\"PF\",\"documento\":\"" + sufixo + "\",\"nome\":\"Cliente atualizado\"}", 200);

        String produtoId = id(autorizado(token, post("/produtos"),
                "{\"codigo\":\"P" + sufixo + "\",\"nome\":\"Produto\"}", 201));
        autorizado(token, put("/produtos/" + produtoId),
                "{\"codigo\":\"P" + sufixo + "\",\"nome\":\"Produto 2\",\"ativo\":true}", 200);

        String recursoId = id(autorizado(token, post("/recursos"),
                "{\"produtoId\":\"" + produtoId + "\",\"codigo\":\"VOLUNTARIOS\",\"nome\":\"Voluntários\",\"tipo\":\"LIMITE\"}",
                201));
        autorizado(token, put("/recursos/" + recursoId),
                "{\"produtoId\":\"" + produtoId + "\",\"codigo\":\"VOLUNTARIOS\",\"nome\":\"Voluntários 2\",\"tipo\":\"LIMITE\"}",
                200);

        String planoId = id(autorizado(token, post("/planos"),
                "{\"produtoId\":\"" + produtoId + "\",\"codigo\":\"PL" + sufixo + "\",\"nome\":\"Plano\",\"recursos\":[{\"recursoId\":\""
                        + recursoId + "\",\"valor\":10}]}", 201));
        autorizado(token, post("/planos/" + planoId + "/precos"),
                "{\"periodicidade\":\"MENSAL\",\"valor\":80.00,\"vigenteDesde\":\"" + hoje + "\"}", 200);
        autorizado(token, put("/planos/" + planoId),
                "{\"produtoId\":\"" + produtoId + "\",\"codigo\":\"PL" + sufixo + "\",\"nome\":\"Plano 2\",\"ativo\":true}", 200);

        String adicionalId = id(autorizado(token, post("/adicionais"),
                "{\"produtoId\":\"" + produtoId + "\",\"recursoId\":\"" + recursoId
                        + "\",\"codigo\":\"AD" + sufixo + "\",\"nome\":\"Pacote\",\"quantidade\":5,\"preco\":10}", 201));
        autorizado(token, put("/adicionais/" + adicionalId),
                "{\"produtoId\":\"" + produtoId + "\",\"recursoId\":\"" + recursoId
                        + "\",\"codigo\":\"AD" + sufixo + "\",\"nome\":\"Pacote 2\",\"quantidade\":5,\"preco\":12,\"ativo\":true}",
                200);

        String contratacaoId = id(autorizado(token, post("/contratacoes"),
                "{\"clienteId\":\"" + clienteId + "\",\"produtoId\":\"" + produtoId + "\",\"planoId\":\"" + planoId
                        + "\",\"periodicidade\":\"MENSAL\",\"valor\":100,\"diaVencimento\":10,\"inicio\":\"" + hoje
                        + "\",\"nomeInstancia\":\"Instancia\",\"slugInstancia\":\"inst-" + sufixo
                        + "\",\"adminNome\":\"Admin\",\"adminEmail\":\"" + sufixo + "@teste.com\"}", 201));
        mockMvc.perform(get("/contratacoes/" + contratacaoId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versaoDireitos").value(1))
                .andExpect(jsonPath("$.direitos.acessoLiberado").value(true))
                .andExpect(jsonPath("$.idempotencyKey").value(contratacaoId));
        autorizado(token, get("/clientes"), null, 200);
        autorizado(token, get("/produtos"), null, 200);
        autorizado(token, get("/recursos"), null, 200);
        autorizado(token, get("/planos"), null, 200);
        autorizado(token, get("/adicionais"), null, 200);
        mockMvc.perform(get("/contratacoes").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + contratacaoId + "')].diaVencimento").value(10));

        autorizado(token, put("/contratacoes/" + contratacaoId + "/adicionais"),
                "{\"adicionais\":[{\"adicionalId\":\"" + adicionalId + "\",\"quantidade\":1}],\"motivo\":\"pacote\"}", 200);
        autorizado(token, put("/contratacoes/" + contratacaoId),
                "{\"nomeInstancia\":\"Instancia 2\",\"slugInstancia\":\"inst-" + sufixo
                        + "-b\",\"adminNome\":\"Admin 2\",\"adminEmail\":\"b" + sufixo + "@teste.com\"}", 200);
        autorizado(token, post("/contratacoes/" + contratacaoId + "/bloquear"),
                "{\"motivo\":\"analise\"}", 200);
        mockMvc.perform(get("/contratacoes/" + contratacaoId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direitos.acessoLiberado").value(false));
        autorizado(token, post("/contratacoes/" + contratacaoId + "/desbloquear"), "{}", 200);

        MvcResult financeiro = autorizado(token, get("/contratacoes/" + contratacaoId + "/financeiro"), null, 200);
        String cobrancaId = com.jayway.jsonpath.JsonPath.read(
                financeiro.getResponse().getContentAsString(), "$.cobrancas[0].id");
        autorizado(token, post("/contratacoes/" + contratacaoId + "/pagamentos"),
                "{\"cobrancaIds\":[\"" + cobrancaId + "\"],\"pagoEm\":\"" + hoje + "\",\"formaPagamento\":\"PIX\"}", 200);
        autorizado(token, post("/contratacoes/" + contratacaoId + "/cobrancas/" + cobrancaId + "/estornar"), null, 200);
        autorizado(token, post("/contratacoes/" + contratacaoId + "/cobrancas/" + cobrancaId + "/isentar"),
                "{\"motivo\":\"cortesia\"}", 200);
        autorizado(token, post("/contratacoes/" + contratacaoId + "/cobrancas/adiantadas"),
                "{\"ate\":\"" + hoje.substring(0, 7) + "\"}", 200);
        autorizado(token, put("/contratacoes/" + contratacaoId + "/plano"),
                "{\"planoId\":\"" + planoId + "\",\"periodicidade\":\"MENSAL\",\"valor\":90,\"diaVencimento\":10,\"aPartirDe\":\""
                        + hoje + "\",\"motivo\":\"ajuste\"}", 200);
        autorizado(token, post("/contratacoes/" + contratacaoId + "/cancelar"),
                "{\"motivo\":\"fim\"}", 200);
    }

    private MvcResult autorizado(String token,
                                 org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
                                 String corpo, int status) throws Exception {
        if (corpo != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        return mockMvc.perform(builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().is(status))
                .andReturn();
    }

    private static String corpoValido(String caminho) {
        String id = UUID.randomUUID().toString();
        if (caminho.startsWith("/clientes")) {
            return "{\"tipo\":\"PF\",\"documento\":\"x\",\"nome\":\"n\"}";
        }
        if (caminho.startsWith("/produtos")) {
            return "{\"codigo\":\"x\",\"nome\":\"n\"}";
        }
        if (caminho.startsWith("/recursos")) {
            return "{\"produtoId\":\"" + id + "\",\"codigo\":\"x\",\"nome\":\"n\",\"tipo\":\"LIMITE\"}";
        }
        if (caminho.contains("/precos")) {
            return "{\"periodicidade\":\"MENSAL\",\"valor\":1,\"vigenteDesde\":\"2026-01-01\"}";
        }
        if (caminho.startsWith("/planos")) {
            return "{\"produtoId\":\"" + id + "\",\"codigo\":\"x\",\"nome\":\"n\"}";
        }
        if (caminho.startsWith("/adicionais")) {
            return "{\"produtoId\":\"" + id + "\",\"recursoId\":\"" + id
                    + "\",\"codigo\":\"x\",\"nome\":\"n\",\"quantidade\":1,\"preco\":1}";
        }
        if (caminho.contains("/bloquear") || caminho.contains("/suporte")) {
            return "{\"motivo\":\"x\"}";
        }
        if (caminho.contains("/pagamentos")) {
            return "{\"cobrancaIds\":[\"" + id + "\"],\"pagoEm\":\"2026-01-01\",\"formaPagamento\":\"PIX\"}";
        }
        if (caminho.contains("/adiantadas")) {
            return "{\"ate\":\"2026-01\"}";
        }
        if (caminho.contains("/plano")) {
            return "{\"planoId\":\"" + id + "\",\"periodicidade\":\"MENSAL\",\"diaVencimento\":10,\"aPartirDe\":\"2026-01-01\"}";
        }
        if (caminho.contains("/adicionais")) {
            return "{\"adicionais\":[]}";
        }
        if (caminho.startsWith("/contratacoes")) {
            return "{\"clienteId\":\"" + id + "\",\"produtoId\":\"" + id + "\",\"planoId\":\"" + id
                    + "\",\"periodicidade\":\"MENSAL\",\"diaVencimento\":10,\"inicio\":\"2026-01-01\","
                    + "\"nomeInstancia\":\"n\",\"slugInstancia\":\"slug\",\"adminNome\":\"a\",\"adminEmail\":\"a@teste.com\"}";
        }
        return "{}";
    }

    private static String id(MvcResult resultado) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(resultado.getResponse().getContentAsString(), "$.id");
    }

    private String token() throws Exception {
        MvcResult login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha-correta-123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
    }

    private static UsernamePasswordAuthenticationToken principalSemPapel() {
        return UsernamePasswordAuthenticationToken.authenticated(
                "integracao:teste", null, List.of(new SimpleGrantedAuthority("PERM_INTEGRACAO")));
    }
}
