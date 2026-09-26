package br.com.central.api.integracao;

import br.com.central.api.AbstractIntegrationTest;
import br.com.central.api.Documentos;
import br.com.central.api.comercial.CatalogoService;
import br.com.central.api.comercial.ClienteService;
import br.com.central.api.comercial.ContratacaoService;
import br.com.central.api.comercial.EventoSaida;
import br.com.central.api.comercial.EventoSaidaRepository;
import br.com.central.api.comercial.HistoricoContratacao;
import br.com.central.api.comercial.HistoricoContratacaoRepository;
import br.com.central.api.comercial.Periodicidade;
import br.com.central.api.comercial.SituacaoComercial;
import br.com.central.api.comercial.SituacaoEvento;
import br.com.central.api.comercial.SituacaoProvisionamento;
import br.com.central.api.comercial.TipoCliente;
import br.com.central.api.comercial.TipoRecurso;
import br.com.central.api.comercial.dto.CatalogoDtos.RecursoDoPlanoRequest;
import br.com.central.api.comercial.dto.CatalogoDtos.SalvarAdicionalRequest;
import br.com.central.api.comercial.dto.CatalogoDtos.SalvarPlanoRequest;
import br.com.central.api.comercial.dto.CatalogoDtos.SalvarProdutoRequest;
import br.com.central.api.comercial.dto.CatalogoDtos.SalvarRecursoRequest;
import br.com.central.api.comercial.dto.ClienteDtos.SalvarClienteRequest;
import br.com.central.api.comercial.dto.ContratacaoDtos.AdicionalContratadoRequest;
import br.com.central.api.comercial.dto.ContratacaoDtos.AtualizarProvisionamentoRequest;
import br.com.central.api.comercial.dto.ContratacaoDtos.ContratacaoResponse;
import br.com.central.api.comercial.dto.ContratacaoDtos.CriarContratacaoRequest;
import br.com.central.api.comercial.dto.ContratacaoDtos.SubstituirAdicionaisRequest;
import br.com.central.api.operador.Operador;
import br.com.central.api.operador.OperadorLogRepository;
import br.com.central.api.operador.OperadorRepository;
import br.com.central.api.security.JwtService;
import br.com.central.api.security.OperadorAutenticado;
import br.com.central.api.web.ConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(EntregaIntegracaoTest.ClienteDeTeste.class)
class EntregaIntegracaoTest extends AbstractIntegrationTest {

    private static final byte[] SEGREDO =
            "segredo-de-teste-nao-usar-em-producao-0123456789".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private EntregaIntegracao entrega;
    @Autowired
    private ContratacaoService contratacaoService;
    @Autowired
    private ClienteService clienteService;
    @Autowired
    private CatalogoService catalogoService;
    @Autowired
    private EventoSaidaRepository eventoRepository;
    @Autowired
    private OperadorRepository operadorRepository;
    @Autowired
    private OperadorLogRepository operadorLogRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private HistoricoContratacaoRepository historicoRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MockMvc mockMvc;

    private final List<String> chaves = new ArrayList<>();
    private final List<String> nonces = new ArrayList<>();
    private String ultimoCorpo = "";

    private UUID operadorId;
    private String emailOperador;
    private UUID clienteId;
    private UUID produtoId;
    private UUID planoId;
    private UUID adicionalId;
    private String slug;
    private ContratacaoResponse criada;

    @BeforeEach
    void preparar() {
        ClienteDeTeste.servidor.reset();
        chaves.clear();
        nonces.clear();
        jdbc.update("update evento_saida set situacao = 'DESCARTADO' where situacao = 'PENDENTE'");
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        emailOperador = sufixo + "@central.test";
        Operador operador = operadorRepository.saveAndFlush(
                new Operador("Ana Operadora", emailOperador, "hash"));
        operadorId = operador.getId();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new OperadorAutenticado(operadorId, emailOperador),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_OPERADOR"))));

        clienteId = clienteService.criar(new SalvarClienteRequest(
                TipoCliente.PF, Documentos.cpf(), "Cliente " + sufixo,
                null, null, null, null, null, null, null, null)).id();
        produtoId = catalogoService.criarProduto(new SalvarProdutoRequest(
                "P" + sufixo, "Produto " + sufixo, "http://app.test", true)).id();
        UUID limite = catalogoService.criarRecurso(new SalvarRecursoRequest(
                produtoId, "VOLUNTARIOS", "Voluntarios", TipoRecurso.LIMITE, "pessoa")).id();
        planoId = catalogoService.criarPlano(new SalvarPlanoRequest(
                produtoId, "PL" + sufixo, "Plano " + sufixo, true,
                List.of(new RecursoDoPlanoRequest(limite, new BigDecimal("100"))))).id();
        adicionalId = catalogoService.criarAdicional(new SalvarAdicionalRequest(
                produtoId, limite, "AD" + sufixo, "Pacote", new BigDecimal("10"), new BigDecimal("15.00"), true)).id();
        slug = "inst-" + sufixo;
        criada = contratar(slug, null);
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void provisionamento201GravaTenantEAssinatura() {
        UUID tenant = UUID.randomUUID();
        esperar("POST", "/integracao/v1/instancias", 201, corpoCriado(criada.id(), tenant));

        entrega.enviarProntos();

        ClienteDeTeste.servidor.verify();
        ContratacaoResponse atual = contratacaoService.buscar(criada.id());
        assertThat(atual.situacaoProvisionamento()).isEqualTo(SituacaoProvisionamento.ATIVA);
        assertThat(atual.idExterno()).isEqualTo(tenant);
        assertThat(atual.direitos().tenantId()).isEqualTo(tenant);
        assertThat(chaves).containsExactly(criada.id().toString());
        assertThat(evento(criada.id()).getSituacao()).isEqualTo(SituacaoEvento.ENVIADO);
    }

    @Test
    void repeticaoUsaAMesmaChaveE5xxEsperaUmMinuto() {
        Instant antes = Instant.now();
        esperar("POST", "/integracao/v1/instancias", 502, "");
        entrega.enviarProntos();
        ClienteDeTeste.servidor.verify();

        EventoSaida pendente = evento(criada.id());
        assertThat(pendente.getSituacao()).isEqualTo(SituacaoEvento.PENDENTE);
        assertThat(pendente.getTentativas()).isEqualTo(1);
        assertThat(pendente.getProximaTentativa()).isAfter(antes.plusSeconds(50));
        assertThat(pendente.getProximaTentativa()).isBefore(antes.plusSeconds(90));
        assertThat(contratacaoService.buscar(criada.id()).situacaoProvisionamento())
                .isEqualTo(SituacaoProvisionamento.PROCESSANDO);

        ClienteDeTeste.servidor.reset();
        entrega.enviarProntos();
        ClienteDeTeste.servidor.verify();

        jdbc.update("update evento_saida set proxima_tentativa = ? where id = ?",
                Timestamp.from(Instant.now().minusSeconds(5)), pendente.getId());
        UUID tenant = UUID.randomUUID();
        esperar("POST", "/integracao/v1/instancias", 201, corpoCriado(criada.id(), tenant));
        entrega.enviarProntos();
        ClienteDeTeste.servidor.verify();

        assertThat(chaves).containsExactly(criada.id().toString(), criada.id().toString());
        assertThat(nonces).hasSize(2);
        assertThat(nonces.get(0)).isNotEqualTo(nonces.get(1));
        assertThat(contratacaoService.buscar(criada.id()).idExterno()).isEqualTo(tenant);
    }

    @Test
    void conflito409NaoRepete() {
        semRepeticao(409);
    }

    @Test
    void slug422NaoRepete() {
        semRepeticao(422);
    }

    @Test
    void versaoObsoletaNaoEEnviada() {
        contratacaoService.substituirAdicionais(criada.id(), new SubstituirAdicionaisRequest(
                List.of(new AdicionalContratadoRequest(adicionalId, BigDecimal.ONE)), "pacote"));
        UUID tenant = UUID.randomUUID();
        esperar("POST", "/integracao/v1/instancias", 201, corpoCriado(criada.id(), tenant));

        entrega.enviarProntos();

        ClienteDeTeste.servidor.verify();
        assertThat(ultimoCorpo).contains("\"versao\":2");
        assertThat(eventoRepository.findByContratacaoId(criada.id()))
                .filteredOn(evento -> evento.getSituacao() == SituacaoEvento.DESCARTADO)
                .isNotEmpty();
        assertThat(eventoRepository.findByContratacaoIdAndSituacao(criada.id(), SituacaoEvento.ENVIADO))
                .singleElement()
                .satisfies(evento -> assertThat(evento.getVersao()).isEqualTo(2));
    }

    @Test
    void canceladaAntesDoEnvioNaoCriaInstancia() {
        esperar("POST", "/integracao/v1/instancias", 502, "");
        entrega.enviarProntos();
        ClienteDeTeste.servidor.verify();
        contratacaoService.cancelar(criada.id(), "desistiu");
        jdbc.update("update evento_saida set proxima_tentativa = ? where contratacao_id = ? and situacao = 'PENDENTE'",
                Timestamp.from(Instant.now().minusSeconds(5)), criada.id());

        ClienteDeTeste.servidor.reset();
        entrega.enviarProntos();
        ClienteDeTeste.servidor.verify();

        assertThat(eventoRepository.findByContratacaoIdAndSituacao(criada.id(), SituacaoEvento.PENDENTE)).isEmpty();
        ContratacaoResponse atual = contratacaoService.buscar(criada.id());
        assertThat(atual.idExterno()).isNull();
        assertThat(historicoRepository.findByContratacaoIdOrderByCriadoEmAsc(criada.id()))
                .extracting(HistoricoContratacao::getAcao)
                .contains("PROVISIONAMENTO_DESCARTADO")
                .doesNotContain("PROVISIONADA");
    }

    @Test
    void webhookFalhouDepoisDe72Horas() {
        UUID tenant = UUID.randomUUID();
        esperar("POST", "/integracao/v1/instancias", 201, corpoCriado(criada.id(), tenant));
        entrega.enviarProntos();
        ClienteDeTeste.servidor.verify();
        ClienteDeTeste.servidor.reset();
        chaves.clear();

        contratacaoService.bloquear(criada.id(), "sumiu");
        EventoSaida pendente = evento(criada.id());
        jdbc.update("update evento_saida set criado_em = ? where id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofHours(73))), pendente.getId());
        esperar("PUT", "/integracao/v1/instancias/" + tenant + "/direitos", 500, "");

        entrega.enviarProntos();

        ClienteDeTeste.servidor.verify();
        assertThat(chaves).containsExactly((String) null);
        assertThat(eventoRepository.findById(pendente.getId()).orElseThrow().getSituacao())
                .isEqualTo(SituacaoEvento.FALHOU);
        ContratacaoResponse atual = contratacaoService.buscar(criada.id());
        assertThat(atual.situacaoComercial()).isEqualTo(SituacaoComercial.BLOQUEADA);
        assertThat(atual.situacaoProvisionamento()).isEqualTo(SituacaoProvisionamento.ATIVA);
    }

    @Test
    void botaoTentaDeNovoDepoisDoErro() throws Exception {
        esperar("POST", "/integracao/v1/instancias", 422, "{\"codigo\":\"SLUG_EM_USO\"}");
        entrega.enviarProntos();
        ClienteDeTeste.servidor.verify();
        assertThat(contratacaoService.buscar(criada.id()).situacaoProvisionamento())
                .isEqualTo(SituacaoProvisionamento.ERRO);

        ClienteDeTeste.servidor.reset();
        chaves.clear();
        UUID tenant = UUID.randomUUID();
        esperar("POST", "/integracao/v1/instancias", 201, corpoCriado(criada.id(), tenant));
        String token = jwtService.gerarAccessToken(operadorId);

        mockMvc.perform(post("/contratacoes/" + criada.id() + "/tentar-provisionamento")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacaoProvisionamento").value("ATIVA"))
                .andExpect(jsonPath("$.idExterno").value(tenant.toString()));
        ClienteDeTeste.servidor.verify();
        assertThat(chaves).containsExactly(criada.id().toString());
    }

    @Test
    void recusa422LiberaEdicaoEReenviaComAMesmaChaveEONovoSlug() {
        esperar("POST", "/integracao/v1/instancias", 422, "{\"codigo\":\"SLUG_EM_USO\"}");
        entrega.enviarProntos();
        ClienteDeTeste.servidor.verify();
        ContratacaoResponse recusada = contratacaoService.buscar(criada.id());
        assertThat(recusada.situacaoProvisionamento()).isEqualTo(SituacaoProvisionamento.ERRO);
        assertThat(recusada.provisionamentoEditavel()).isTrue();

        ContratacaoResponse editada = contratacaoService.atualizarProvisionamento(criada.id(),
                new AtualizarProvisionamentoRequest("Instancia nova", slug + "-novo", "Admin", slug + "@teste.com"));
        assertThat(editada.situacaoProvisionamento()).isEqualTo(SituacaoProvisionamento.PENDENTE);
        assertThat(editada.idempotencyKey()).isEqualTo(criada.id());

        ClienteDeTeste.servidor.reset();
        chaves.clear();
        UUID tenant = UUID.randomUUID();
        esperar("POST", "/integracao/v1/instancias", 201, corpoCriado(criada.id(), tenant));
        entrega.enviarProntos();
        ClienteDeTeste.servidor.verify();

        assertThat(chaves).containsExactly(criada.id().toString());
        assertThat(ultimoCorpo).contains("\"slug\":\"" + slug + "-novo\"");
        ContratacaoResponse provisionada = contratacaoService.buscar(criada.id());
        assertThat(provisionada.idExterno()).isEqualTo(tenant);
        assertThat(provisionada.provisionamentoEditavel()).isFalse();
    }

    @Test
    void semRespostaDoAppTravaAEdicao() {
        esperar("POST", "/integracao/v1/instancias", 502, "");
        entrega.enviarProntos();
        ClienteDeTeste.servidor.verify();

        assertThat(contratacaoService.buscar(criada.id()).provisionamentoEditavel()).isFalse();
        assertEdicaoRecusada();
    }

    @Test
    void conflito409TravaAEdicao() {
        semRepeticao(409);

        assertThat(contratacaoService.buscar(criada.id()).provisionamentoEditavel()).isFalse();
        assertEdicaoRecusada();
    }

    @Test
    void instanciaProvisionadaNaoTemMaisNomeSlugOuAdminEditaveis() {
        esperar("POST", "/integracao/v1/instancias", 201, corpoCriado(criada.id(), UUID.randomUUID()));
        entrega.enviarProntos();
        ClienteDeTeste.servidor.verify();

        assertEdicaoRecusada();
        ContratacaoResponse atual = contratacaoService.buscar(criada.id());
        assertThat(atual.slugInstancia()).isEqualTo(slug);
        assertThat(atual.versaoDireitos()).isEqualTo(1);
    }

    @Test
    void mesmosDadosNaoContamComoEdicao() {
        esperar("POST", "/integracao/v1/instancias", 201, corpoCriado(criada.id(), UUID.randomUUID()));
        entrega.enviarProntos();
        ClienteDeTeste.servidor.verify();

        ContratacaoResponse igual = contratacaoService.atualizarProvisionamento(criada.id(),
                new AtualizarProvisionamentoRequest(criada.nomeInstancia(), criada.slugInstancia(),
                        criada.adminNome(), criada.adminEmail()));
        assertThat(igual.versaoDireitos()).isEqualTo(1);
    }

    private void assertEdicaoRecusada() {
        assertThatThrownBy(() -> contratacaoService.atualizarProvisionamento(criada.id(),
                new AtualizarProvisionamentoRequest("Outro nome", slug + "-x", "Outro", "outro@teste.com")))
                .isInstanceOf(ConflictException.class)
                .satisfies(e -> assertThat(((ConflictException) e).getCodigo())
                        .isEqualTo("PROVISIONAMENTO_NAO_EDITAVEL"));
    }

    @Test
    void suporteDevolveUrlEAudita() throws Exception {
        UUID tenant = UUID.randomUUID();
        esperar("POST", "/integracao/v1/instancias", 201, corpoCriado(criada.id(), tenant));
        entrega.enviarProntos();
        ClienteDeTeste.servidor.verify();
        ClienteDeTeste.servidor.reset();

        esperar("POST", "/integracao/v1/instancias/" + tenant + "/suporte", 201,
                "{\"codigo\":\"ABCD-EFGH-IJKL\",\"urlAcesso\":\"http://localhost:4200/suporte?codigo=ABCD-EFGH-IJKL\",\"expiraEm\":\"2026-09-25T18:02:00Z\"}");
        String token = jwtService.gerarAccessToken(operadorId);

        mockMvc.perform(post("/contratacoes/" + criada.id() + "/suporte")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"paroquia sem acesso\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("ABCD-EFGH-IJKL"))
                .andExpect(jsonPath("$.urlAcesso").value("http://localhost:4200/suporte?codigo=ABCD-EFGH-IJKL"))
                .andExpect(jsonPath("$.expiraEm").value("2026-09-25T18:02:00Z"));
        ClienteDeTeste.servidor.verify();
        assertThat(ultimoCorpo).contains(emailOperador).contains("paroquia sem acesso");
        assertThat(operadorLogRepository.findByAcao("SUPORTE"))
                .anySatisfy(log -> {
                    assertThat(log.getOperadorId()).isEqualTo(operadorId);
                    assertThat(log.getDetalhe()).contains(criada.id().toString());
                    assertThat(log.getIp()).isNotBlank();
                });
    }

    @Test
    void recursosDoAppVemAssinadosEMarcamOQueJaEstaCadastrado() throws Exception {
        catalogoService.criarRecurso(new SalvarRecursoRequest(produtoId, "voluntarios", "Voluntários", TipoRecurso.LIMITE, "pessoa"));
        esperar("GET", "/integracao/v1/recursos", 200, """
                [{"codigo":"voluntarios","nome":"Voluntários","tipo":"LIMITE","unidade":"pessoa","aplicado":false},
                 {"codigo":"ESCALAS","nome":"Escalas","tipo":"FUNCIONALIDADE","unidade":null,"aplicado":true},
                 {"codigo":"NOVO_TIPO","nome":"Desconhecido","tipo":"OUTRO","aplicado":false}]""");
        String token = jwtService.gerarAccessToken(operadorId);

        mockMvc.perform(get("/produtos/" + produtoId + "/recursos-do-app").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].codigo").value("voluntarios"))
                .andExpect(jsonPath("$[0].cadastrado").value(true))
                .andExpect(jsonPath("$[1].codigo").value("ESCALAS"))
                .andExpect(jsonPath("$[1].tipo").value("FUNCIONALIDADE"))
                .andExpect(jsonPath("$[1].aplicado").value(true))
                .andExpect(jsonPath("$[1].cadastrado").value(false));
        ClienteDeTeste.servidor.verify();

        ClienteDeTeste.servidor.reset();
        esperar("GET", "/integracao/v1/recursos", 500, "");
        mockMvc.perform(get("/produtos/" + produtoId + "/recursos-do-app").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rollbackNaoDeixaEvento() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> template.execute(status -> {
            contratacaoService.bloquear(criada.id(), "vai falhar");
            throw new IllegalStateException("falha de teste");
        })).hasMessageContaining("falha de teste");

        assertThat(eventoRepository.findByContratacaoId(criada.id()))
                .singleElement()
                .satisfies(evento -> {
                    assertThat(evento.getSituacao()).isEqualTo(SituacaoEvento.PENDENTE);
                    assertThat(evento.getVersao()).isEqualTo(1);
                });
        assertThat(contratacaoService.buscar(criada.id()).versaoDireitos()).isEqualTo(1);
    }

    private void semRepeticao(int status) {
        esperar("POST", "/integracao/v1/instancias", status, "");
        entrega.enviarProntos();
        ClienteDeTeste.servidor.verify();
        assertThat(evento(criada.id()).getSituacao()).isEqualTo(SituacaoEvento.FALHOU);
        assertThat(contratacaoService.buscar(criada.id()).situacaoProvisionamento())
                .isEqualTo(SituacaoProvisionamento.ERRO);

        ClienteDeTeste.servidor.reset();
        entrega.enviarProntos();
        ClienteDeTeste.servidor.verify();
    }

    private EventoSaida evento(UUID contratacaoId) {
        List<EventoSaida> todos = eventoRepository.findByContratacaoId(contratacaoId);
        return todos.stream().filter(evento -> evento.getSituacao() == SituacaoEvento.PENDENTE).findFirst()
                .or(() -> todos.stream().filter(evento -> evento.getSituacao() == SituacaoEvento.ENVIADO).findFirst())
                .or(() -> todos.stream().filter(evento -> evento.getSituacao() == SituacaoEvento.FALHOU).findFirst())
                .orElseThrow();
    }

    private void esperar(String verbo, String caminho, int status, String corpo) {
        var expectativa = ClienteDeTeste.servidor
                .expect(requestTo("http://app.test" + caminho))
                .andExpect(method(HttpMethod.valueOf(verbo)))
                .andExpect(this::conferirAssinatura);
        if (corpo == null || corpo.isEmpty()) {
            expectativa.andRespond(withStatus(HttpStatus.valueOf(status)));
        } else {
            expectativa.andRespond(withStatus(HttpStatus.valueOf(status))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corpo));
        }
    }

    private void conferirAssinatura(org.springframework.http.client.ClientHttpRequest request) {
        MockClientHttpRequest mock = (MockClientHttpRequest) request;
        byte[] corpo = mock.getBodyAsString().getBytes(StandardCharsets.UTF_8);
        ultimoCorpo = mock.getBodyAsString();
        String caminho = request.getURI().getPath();
        String timestamp = request.getHeaders().getFirst(IntegracaoFiltro.TIMESTAMP);
        String nonce = request.getHeaders().getFirst(IntegracaoFiltro.NONCE);
        String esperada = HmacAssinatura.assinar(
                SEGREDO, request.getMethod().name(), caminho, timestamp, nonce, corpo);
        assertThat(request.getHeaders().getFirst(IntegracaoFiltro.ASSINATURA)).isEqualTo(esperada);
        assertThat(request.getHeaders().getFirst(IntegracaoFiltro.CHAVE)).isEqualTo("teste-central");
        chaves.add(request.getHeaders().getFirst("Idempotency-Key"));
        nonces.add(nonce);
    }

    private ContratacaoResponse contratar(String slugInstancia, List<AdicionalContratadoRequest> adicionais) {
        return contratacaoService.criar(new CriarContratacaoRequest(
                clienteId, produtoId, planoId, Periodicidade.MENSAL, new BigDecimal("100.00"),
                10, LocalDate.now(), SituacaoComercial.TRIAL,
                "Instancia " + slugInstancia, slugInstancia, "Admin", slugInstancia + "@teste.com",
                null, adicionais));
    }

    private static String corpoCriado(UUID contratacaoId, UUID tenantId) {
        return "{\"contratacaoId\":\"" + contratacaoId + "\",\"tenantId\":\"" + tenantId
                + "\",\"administrador\":{\"usuarioId\":\"" + UUID.randomUUID()
                + "\",\"situacao\":\"CONVIDADO\"},\"versaoDireitosAplicada\":1,\"criadoEm\":\"2026-09-25T12:00:00Z\"}";
    }

    @TestConfiguration
    static class ClienteDeTeste {
        static MockRestServiceServer servidor;

        @Bean
        @Primary
        AplicativoHttp aplicativoHttpDeTeste() {
            RestClient.Builder builder = RestClient.builder();
            servidor = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
            return new AplicativoHttp(
                    builder.build(),
                    "teste-central",
                    SEGREDO,
                    Clock.systemUTC(),
                    () -> UUID.randomUUID().toString());
        }
    }
}
