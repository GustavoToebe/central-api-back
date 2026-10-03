package br.com.central.api.operador;

import br.com.central.api.AbstractIntegrationTest;
import br.com.central.api.security.JwtService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@AutoConfigureMockMvc
@TestPropertySource(properties = {"CENTRAL_MFA_CHAVES=teste:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "CENTRAL_MFA_CHAVE_ATIVA=teste", "central.auth.limite-ip=10000", "central.auth.limite-conta=10000"})
class MfaHttpIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc; @Autowired OperadorRepository operadores;
    @Autowired PasswordEncoder senhas; @Autowired JwtService jwt; @Autowired JdbcTemplate jdbc;
    @Autowired MfaService mfa; @Autowired AuthService auth;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    private UUID id; private String email; private String access;
    @BeforeEach void criar() {
        email = "mfa-" + UUID.randomUUID() + "@central.test";
        id = operadores.saveAndFlush(new Operador("Operador MFA", email, senhas.encode("senha-correta"))).getId();
        access = jwt.gerarAccessToken(id);
    }
    @Test void ativacaoConfirmaSegredoEncerraSessoesELoginExigeSegundoFator() throws Exception {
        MvcResult antes = login(null).andExpect(status().isOk()).andReturn();
        String cookie = antes.getResponse().getCookie(AuthController.REFRESH_TOKEN_COOKIE).getValue();
        String segredo = preparar();
        acao("ativar", "senha-correta", "0000000").andExpect(status().isUnauthorized());
        List<String> codigos = ativar(segredo);
        assertThat(codigos).hasSize(10).doesNotHaveDuplicates();
        assertThat(operadores.findById(id).orElseThrow().getMfaSegredo()).doesNotContain(segredo);
        assertThat(jdbc.queryForObject("select count(*) from operador_mfa_recuperacao where operador_id = ? and codigo_hash = ?", Integer.class, id, codigos.getFirst())).isZero();
        mvc.perform(get("/operadores/eu").header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/refresh").with(csrf()).cookie(new jakarta.servlet.http.Cookie(AuthController.REFRESH_TOKEN_COOKIE, cookie))).andExpect(status().isUnauthorized());
        login(null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("MFA_NECESSARIO"))
                .andExpect(result -> assertThat(result.getResponse().getCookie(AuthController.REFRESH_TOKEN_COOKIE)).isNull());
        // O passo usado para confirmar a ativação também já foi consumido.
        login(CodigoTotpTeste.codigo(segredo, Instant.now().getEpochSecond() / 30, 6)).andExpect(status().isUnauthorized());
        MvcResult sessao = login(codigos.getFirst()).andExpect(status().isOk()).andReturn();
        access = JsonPath.read(sessao.getResponse().getContentAsString(), "$.accessToken");
        login(codigos.getFirst()).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("MFA_INVALIDO"));
        mvc.perform(get("/operadores/eu/mfa").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.codigosRestantes").value(9)).andExpect(jsonPath("$.segredo").doesNotExist());
        acao("desativar", "errada", codigos.get(1)).andExpect(status().isUnauthorized());
        acao("desativar", "senha-correta", codigos.get(1)).andExpect(status().isNoContent());
        mvc.perform(get("/operadores/eu").header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
        login(null).andExpect(status().isOk());
    }
    @Test void preparacaoExigeSenhaEConfereExpiracaoSemAtivar() throws Exception {
        acao("preparar", "errada", null).andExpect(status().isUnauthorized());
        String segredo = preparar();
        jdbc.update("update operador set mfa_pendente_ate = now() - interval '1 second' where id = ?", id);
        acao("ativar", "senha-correta", CodigoTotpTeste.codigo(segredo, Instant.now().getEpochSecond() / 30, 6))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("MFA_PREPARACAO_EXPIRADA"));
        login(null).andExpect(status().isOk());
        mvc.perform(get("/operadores/eu/mfa")).andExpect(status().isUnauthorized());
    }
    @Test void codigoRecuperacaoConcorrenteEmiteSomenteUmaSessao() throws Exception {
        List<String> codigos = ativar(preparar());
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var inicio = new java.util.concurrent.CountDownLatch(1);
            var a = pool.submit(() -> {inicio.await(); return login(codigos.getFirst()).andReturn().getResponse().getStatus();});
            var b = pool.submit(() -> {inicio.await(); return login(codigos.getFirst()).andReturn().getResponse().getStatus();});
            inicio.countDown();
            assertThat(List.of(a.get(15, java.util.concurrent.TimeUnit.SECONDS), b.get(15, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 401);
        }
    }

    @Test void totpNovoAceitoUmaVezESenhaErradaNaoRevelaMfa() throws Exception {
        String segredo = preparar(); ativar(segredo);
        mvc.perform(post("/auth/login").contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"senha\":\"errada\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").doesNotExist());
        String codigo = CodigoTotpTeste.codigo(segredo, Instant.now().getEpochSecond() / 30 + 1, 6);
        login(codigo).andExpect(status().isOk());
        login(codigo).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("MFA_INVALIDO"));
    }

    @Test void novaPreparacaoSubstituiAnteriorSemAtivarProtecao() throws Exception {
        String anterior = preparar(); String nova = preparar(); assertThat(nova).isNotEqualTo(anterior);
        mvc.perform(get("/operadores/eu/mfa").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(false));
        login(null).andExpect(status().isOk());
    }

    @Test void refreshQueAguardavaLockNaoEmiteSessaoAposAtivacaoMfa() throws Exception {
        MvcResult sessao = login(null).andExpect(status().isOk()).andReturn();
        String cookie = sessao.getResponse().getCookie(AuthController.REFRESH_TOKEN_COOKIE).getValue();
        String segredo = preparar();
        try (var pool = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var futuro = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<AuthService.Sessao>>();
            new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                operadores.buscarParaAlterar(id).orElseThrow();
                futuro.set(pool.submit(() -> auth.renovar(cookie, "127.0.0.1")));
                // Confirmar que a rotação já leu a projeção e está esperando o lock real do PostgreSQL.
                boolean esperando = false;
                for (int i = 0; i < 100; i++) {
                    // Estatísticas são cacheadas na transação; descartar o snapshot antes de observar outro backend.
                    jdbc.execute("select pg_stat_clear_snapshot()");
                    Integer locks = jdbc.queryForObject("select count(*) from pg_stat_activity where wait_event_type = 'Lock' and query ilike '%for no key update%'", Integer.class);
                    if (locks > 0) {esperando = true; break;}
                    try {Thread.sleep(50);} catch (InterruptedException ex) {Thread.currentThread().interrupt(); throw new IllegalStateException(ex);}
                }
                assertThat(esperando).as("refresh esperando trava do operador").isTrue();
                mfa.ativar(id, "senha-correta", CodigoTotpTeste.codigo(segredo, Instant.now().getEpochSecond() / 30, 6), "127.0.0.1");
            });
            assertThatThrownBy(() -> futuro.get().get(15, java.util.concurrent.TimeUnit.SECONDS))
                    .hasCauseInstanceOf(br.com.central.api.web.UnauthorizedException.class);
        }
        assertThat(jdbc.queryForObject("select count(*) from refresh_token where operador_id = ?", Integer.class, id)).isZero();
    }
    private String preparar() throws Exception {
        MvcResult result = acao("preparar", "senha-correta", null).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.segredo");
    }
    private List<String> ativar(String segredo) throws Exception {
        MvcResult result = acao("ativar", "senha-correta", CodigoTotpTeste.codigo(segredo, Instant.now().getEpochSecond() / 30, 6)).andExpect(status().isOk()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.codigos");
    }
    private ResultActions acao(String acao, String senha, String codigo) throws Exception {
        String body = "{\"senha\":\"" + senha + "\"" + (codigo == null ? "" : ",\"codigo\":\"" + codigo + "\"") + "}";
        return mvc.perform(post("/operadores/eu/mfa/" + acao).header("Authorization", "Bearer " + access).contentType("application/json").content(body));
    }
    private ResultActions login(String codigo) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"senha\":\"senha-correta\"" + (codigo == null ? "" : ",\"codigoMfa\":\"" + codigo + "\"") + "}";
        return mvc.perform(post("/auth/login").contentType("application/json").content(body));
    }
}
