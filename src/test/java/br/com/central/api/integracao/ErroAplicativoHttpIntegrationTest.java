package br.com.central.api.integracao;

import br.com.central.api.AbstractIntegrationTest;
import br.com.central.api.Documentos;
import br.com.central.api.comercial.CatalogoService;
import br.com.central.api.comercial.ClienteService;
import br.com.central.api.comercial.ComercialConfiguration;
import br.com.central.api.comercial.Contratacao;
import br.com.central.api.comercial.ContratacaoRepository;
import br.com.central.api.comercial.ContratacaoService;
import br.com.central.api.comercial.Periodicidade;
import br.com.central.api.comercial.SituacaoComercial;
import br.com.central.api.comercial.TipoCliente;
import br.com.central.api.comercial.dto.CatalogoDtos.SalvarPlanoRequest;
import br.com.central.api.comercial.dto.CatalogoDtos.SalvarProdutoRequest;
import br.com.central.api.comercial.dto.ClienteDtos.SalvarClienteRequest;
import br.com.central.api.comercial.dto.ContratacaoDtos.CriarContratacaoRequest;
import br.com.central.api.operador.Operador;
import br.com.central.api.operador.OperadorRepository;
import br.com.central.api.security.JwtService;
import br.com.central.api.security.OperadorAutenticado;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tela "Logs" (26/09/2026): o app relata erros de servidor e o operador consulta. */
@AutoConfigureMockMvc
class ErroAplicativoHttpIntegrationTest extends AbstractIntegrationTest {

    private static final byte[] SEGREDO =
            "segredo-de-teste-nao-usar-em-producao-0123456789".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ClienteService clienteService;
    @Autowired
    private CatalogoService catalogoService;
    @Autowired
    private ContratacaoService contratacaoService;
    @Autowired
    private ContratacaoRepository contratacaoRepository;
    @Autowired
    private OperadorRepository operadorRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ErroAplicativoService erroService;
    @Autowired
    private JdbcTemplate jdbc;

    private String token;
    private String codigo;
    private UUID produtoId;
    private UUID contratacaoId;
    private UUID tenant;
    private String clienteNome;

    @BeforeEach
    void preparar() {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        Operador operador = operadorRepository.saveAndFlush(new Operador("Ana", sufixo + "@central.test", "hash"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new OperadorAutenticado(operador.getId(), operador.getEmail()), null,
                List.of(new SimpleGrantedAuthority("ROLE_OPERADOR"))));
        token = jwtService.gerarAccessToken(operador.getId());
        clienteNome = "Paroquia Logs " + sufixo;
        UUID clienteId = clienteService.criar(new SalvarClienteRequest(
                TipoCliente.PJ, Documentos.cnpj(), clienteNome, null, null, null, null, null, null, null, null)).id();
        codigo = "L" + sufixo;
        produtoId = catalogoService.criarProduto(new SalvarProdutoRequest(codigo, "Produto", "http://app.test", true)).id();
        UUID planoId = catalogoService.criarPlano(new SalvarPlanoRequest(produtoId, "PL" + sufixo, "Plano", true, List.of())).id();
        contratacaoId = contratacaoService.criar(new CriarContratacaoRequest(
                clienteId, produtoId, planoId, Periodicidade.MENSAL, new BigDecimal("10.00"), 10, LocalDate.now(ComercialConfiguration.FUSO),
                SituacaoComercial.ATIVA, "Instancia " + sufixo, "log-" + sufixo, "Admin", sufixo + "@teste.com",
                null, null)).id();
        tenant = UUID.randomUUID();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Contratacao contratacao = contratacaoRepository.buscarComReferencias(contratacaoId);
            contratacao.setIdExterno(tenant);
        });
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void appRelataErroEOperadorVeNaTelaComClienteEFiltros() throws Exception {
        UUID erroId = UUID.randomUUID();
        UUID usuario = UUID.randomUUID();
        String lote = """
                {"erros":[{"id":"%s","ocorridoEm":"%s","tenantId":"%s","usuarioId":"%s","metodo":"POST",
                  "rota":"/pessoas","status":503,"mensagem":"Storage não configurado.","requestId":"req-123"},
                 {"id":"%s","ocorridoEm":"%s","metodo":"POST","rota":"/auth/login","status":500,
                  "mensagem":"Erro inesperado (NullPointerException)"}]}
                """.formatted(erroId, Instant.now(), tenant, usuario, UUID.randomUUID(), Instant.now());

        enviar(lote).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.recebidos").value(2))
                .andExpect(jsonPath("$.gravados").value(2));
        enviar(lote).andExpect(status().isAccepted()).andExpect(jsonPath("$.gravados").value(0));
        mockMvc.perform(post("/integracao/v1/produtos/" + codigo + "/erros")
                        .contentType(MediaType.APPLICATION_JSON).content(lote))
                .andExpect(status().isUnauthorized());

        String hoje = LocalDate.now(ComercialConfiguration.FUSO).toString();
        mockMvc.perform(get("/erros").param("busca", "storage").param("de", hoje).param("ate", hoje)
                        .param("produtoId", produtoId.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(erroId.toString()))
                .andExpect(jsonPath("$[0].clienteNome").value(clienteNome))
                .andExpect(jsonPath("$[0].contratacaoId").value(contratacaoId.toString()))
                .andExpect(jsonPath("$[0].usuarioId").value(usuario.toString()))
                .andExpect(jsonPath("$[0].status").value(503));
        mockMvc.perform(get("/erros").param("contratacaoId", contratacaoId.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/erros").param("produtoId", produtoId.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(2));
        String amanha = LocalDate.now(ComercialConfiguration.FUSO).plusDays(1).toString();
        mockMvc.perform(get("/erros").param("produtoId", produtoId.toString()).param("de", amanha)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/erros")).andExpect(status().isUnauthorized());
    }

    @Test
    void registrosComMaisDe90DiasSaoApagados() throws Exception {
        UUID velho = UUID.randomUUID();
        enviar("""
                {"erros":[{"id":"%s","ocorridoEm":"%s","status":500}]}
                """.formatted(velho, Instant.now())).andExpect(status().isAccepted());
        jdbc.update("update erro_aplicativo set recebido_em = now() - interval '91 days' where id = ?", velho);

        erroService.apagarAntigos();

        assertThat(jdbc.queryForObject("select count(*) from erro_aplicativo where id = ?", Integer.class, velho))
                .isZero();
    }

    private ResultActions enviar(String corpo) throws Exception {
        String caminho = "/integracao/v1/produtos/" + codigo + "/erros";
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        String agora = Long.toString(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        return mockMvc.perform(post(caminho)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bytes)
                .header(IntegracaoFiltro.CHAVE, "teste-servire")
                .header(IntegracaoFiltro.TIMESTAMP, agora)
                .header(IntegracaoFiltro.NONCE, nonce)
                .header(IntegracaoFiltro.ASSINATURA, HmacAssinatura.assinar(SEGREDO, "POST", caminho, agora, nonce, bytes)));
    }
}
