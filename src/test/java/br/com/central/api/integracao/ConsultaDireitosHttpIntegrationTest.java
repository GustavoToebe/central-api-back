package br.com.central.api.integracao;

import br.com.central.api.AbstractIntegrationTest;
import br.com.central.api.Documentos;
import br.com.central.api.comercial.CatalogoService;
import br.com.central.api.comercial.ClienteService;
import br.com.central.api.comercial.Contratacao;
import br.com.central.api.comercial.ContratacaoRepository;
import br.com.central.api.comercial.ContratacaoService;
import br.com.central.api.comercial.Periodicidade;
import br.com.central.api.comercial.SituacaoComercial;
import br.com.central.api.comercial.SituacaoProvisionamento;
import br.com.central.api.comercial.TipoCliente;
import br.com.central.api.comercial.TipoRecurso;
import br.com.central.api.comercial.dto.CatalogoDtos.RecursoDoPlanoRequest;
import br.com.central.api.comercial.dto.CatalogoDtos.SalvarPlanoRequest;
import br.com.central.api.comercial.dto.CatalogoDtos.SalvarProdutoRequest;
import br.com.central.api.comercial.dto.CatalogoDtos.SalvarRecursoRequest;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ConsultaDireitosHttpIntegrationTest extends AbstractIntegrationTest {

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private IntegracaoProperties properties;

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

    private UUID operadorId;
    private String codigo;
    private UUID cancelada;
    private UUID bloqueada;
    private UUID tenantCancelada;
    private UUID tenantBloqueada;

    @BeforeEach
    void preparar() {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        Operador operador = operadorRepository.saveAndFlush(
                new Operador("Ana", sufixo + "@central.test", "hash"));
        operadorId = operador.getId();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new OperadorAutenticado(operadorId, operador.getEmail()),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_OPERADOR"))));

        UUID clienteId = clienteService.criar(new SalvarClienteRequest(
                TipoCliente.PF, Documentos.cpf(), "Cliente " + sufixo,
                null, null, null, null, null, null, null, null)).id();
        codigo = "P" + sufixo;
        org.mockito.Mockito.when(properties.chaves()).thenReturn(java.util.Map.of("teste-servire",SEGREDO));
        org.mockito.Mockito.when(properties.produtos()).thenReturn(java.util.Map.of("teste-servire",codigo));
        UUID produtoId = catalogoService.criarProduto(new SalvarProdutoRequest(
                codigo, "Produto " + sufixo, "http://app.test", true)).id();
        UUID limite = catalogoService.criarRecurso(new SalvarRecursoRequest(
                produtoId, "VOLUNTARIOS", "Voluntarios", TipoRecurso.LIMITE, "pessoa")).id();
        UUID planoId = catalogoService.criarPlano(new SalvarPlanoRequest(
                produtoId, "PL" + sufixo, "Plano", true,
                List.of(new RecursoDoPlanoRequest(limite, new BigDecimal("10"))))).id();

        cancelada = contratar(clienteId, produtoId, planoId, "a-" + sufixo);
        bloqueada = contratar(clienteId, produtoId, planoId, "b-" + sufixo);
        UUID solta = contratar(clienteId, produtoId, planoId, "c-" + sufixo);

        UUID outroProduto = catalogoService.criarProduto(new SalvarProdutoRequest(
                "O" + sufixo, "Outro", "http://app.test", true)).id();
        UUID outroPlano = catalogoService.criarPlano(new SalvarPlanoRequest(
                outroProduto, "PO" + sufixo, "Plano outro", true, List.of())).id();
        UUID deOutroProduto = contratar(clienteId, outroProduto, outroPlano, "d-" + sufixo);

        tenantCancelada = UUID.randomUUID();
        tenantBloqueada = UUID.randomUUID();
        marcarProvisionada(cancelada, tenantCancelada);
        marcarProvisionada(bloqueada, tenantBloqueada);
        marcarProvisionada(deOutroProduto, UUID.randomUUID());
        contratacaoService.cancelar(cancelada, "fim");
        contratacaoService.bloquear(bloqueada, "divida");
        org.assertj.core.api.Assertions.assertThat(solta).isNotNull();
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listaSoProvisionadasComPaginacao() throws Exception {
        String pagina0 = "/integracao/v1/produtos/" + codigo + "/direitos?pagina=0&tamanho=1";
        assinado(pagina0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanho").value(1))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.itens.length()").value(1))
                .andExpect(jsonPath("$.itens[0].contratacaoId").value(cancelada.toString()))
                .andExpect(jsonPath("$.itens[0].tenantId").value(tenantCancelada.toString()))
                .andExpect(jsonPath("$.itens[0].situacao").value("CANCELADA"))
                .andExpect(jsonPath("$.itens[0].acessoLiberado").value(false));

        String pagina1 = "/integracao/v1/produtos/" + codigo + "/direitos?pagina=1&tamanho=1";
        assinado(pagina1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.itens[0].contratacaoId").value(bloqueada.toString()))
                .andExpect(jsonPath("$.itens[0].tenantId").value(tenantBloqueada.toString()))
                .andExpect(jsonPath("$.itens[0].situacao").value("BLOQUEADA"))
                .andExpect(jsonPath("$.itens[0].acessoLiberado").value(false));
    }

    @Test
    void semAssinaturaRecebe401() throws Exception {
        mockMvc.perform(get("/integracao/v1/produtos/" + codigo + "/direitos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CHAVE_DESCONHECIDA"));
    }

    @Test
    void operadorSemHmacRecebe401() throws Exception {
        String token = jwtService.gerarAccessToken(operadorId);
        mockMvc.perform(get("/integracao/v1/produtos/" + codigo + "/direitos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CHAVE_DESCONHECIDA"));
    }

    private UUID contratar(UUID clienteId, UUID produto, UUID plano, String slugInstancia) {
        return contratacaoService.criar(new CriarContratacaoRequest(
                clienteId, produto, plano, Periodicidade.MENSAL, new BigDecimal("80.00"),
                10, LocalDate.now(), SituacaoComercial.ATIVA,
                "Instancia " + slugInstancia, slugInstancia, "Admin", slugInstancia + "@teste.com",
                null, null)).id();
    }

    private void marcarProvisionada(UUID contratacaoId, UUID tenantId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Contratacao contratacao = contratacaoRepository.buscarComReferencias(contratacaoId);
            contratacao.setIdExterno(tenantId);
            contratacao.setSituacaoProvisionamento(SituacaoProvisionamento.ATIVA);
        });
    }

    private org.springframework.test.web.servlet.ResultActions assinado(String caminho) throws Exception {
        String agora = Long.toString(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String assinatura = HmacAssinatura.assinar(SEGREDO, "GET", caminho, agora, nonce, new byte[0]);
        return mockMvc.perform(get(caminho)
                .header(IntegracaoFiltro.CHAVE, "teste-servire")
                .header(IntegracaoFiltro.TIMESTAMP, agora)
                .header(IntegracaoFiltro.NONCE, nonce)
                .header(IntegracaoFiltro.ASSINATURA, assinatura));
    }
}
