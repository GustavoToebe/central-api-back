package br.com.central.api.comercial;

import br.com.central.api.AbstractIntegrationTest;
import br.com.central.api.comercial.dto.CatalogoDtos.RecursoDoPlanoRequest;
import br.com.central.api.comercial.dto.CatalogoDtos.SalvarPlanoRequest;
import br.com.central.api.comercial.dto.CatalogoDtos.SalvarProdutoRequest;
import br.com.central.api.comercial.dto.CatalogoDtos.SalvarRecursoRequest;
import br.com.central.api.comercial.dto.ClienteDtos.SalvarClienteRequest;
import br.com.central.api.comercial.dto.ContratacaoDtos.AdicionalContratadoRequest;
import br.com.central.api.comercial.dto.ContratacaoDtos.AtualizarProvisionamentoRequest;
import br.com.central.api.comercial.dto.ContratacaoDtos.ContratacaoResponse;
import br.com.central.api.comercial.dto.ContratacaoDtos.CriarContratacaoRequest;
import br.com.central.api.comercial.dto.ContratacaoDtos.HistoricoResponse;
import br.com.central.api.comercial.dto.ContratacaoDtos.SubstituirAdicionaisRequest;
import br.com.central.api.operador.Operador;
import br.com.central.api.operador.OperadorRepository;
import br.com.central.api.security.OperadorAutenticado;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DireitosIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ClienteService clienteService;
    @Autowired
    private CatalogoService catalogoService;
    @Autowired
    private ContratacaoService contratacaoService;
    @Autowired
    private EventoSaidaRepository eventoRepository;
    @Autowired
    private OperadorRepository operadorRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID operadorId;
    private UUID clienteId;
    private UUID produtoId;
    private UUID planoId;
    private UUID adicionalId;
    private String slug;

    @BeforeEach
    void preparar() {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        Operador operador = operadorRepository.saveAndFlush(
                new Operador("Operador " + sufixo, sufixo + "@central.test", "hash"));
        operadorId = operador.getId();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new OperadorAutenticado(operadorId, operador.getEmail()),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_OPERADOR"))));

        clienteId = clienteService.criar(new SalvarClienteRequest(
                TipoCliente.PF, sufixo, "Cliente " + sufixo,
                null, null, null, null, null, null, null, null)).id();
        produtoId = catalogoService.criarProduto(new SalvarProdutoRequest(
                "P" + sufixo, "Produto " + sufixo, "http://localhost", true)).id();
        UUID limite = catalogoService.criarRecurso(new SalvarRecursoRequest(
                produtoId, "VOLUNTARIOS", "Voluntários", TipoRecurso.LIMITE, "pessoa")).id();
        UUID funcionalidade = catalogoService.criarRecurso(new SalvarRecursoRequest(
                produtoId, "ESCALAS", "Escalas", TipoRecurso.FUNCIONALIDADE, null)).id();
        planoId = catalogoService.criarPlano(new SalvarPlanoRequest(
                produtoId, "PL" + sufixo, "Plano " + sufixo, true, List.of(
                new RecursoDoPlanoRequest(limite, new BigDecimal("100")),
                new RecursoDoPlanoRequest(funcionalidade, BigDecimal.ONE)))).id();
        adicionalId = catalogoService.criarAdicional(new br.com.central.api.comercial.dto.CatalogoDtos.SalvarAdicionalRequest(
                produtoId, limite, "AD" + sufixo, "Pacote", new BigDecimal("10"), new BigDecimal("15.00"), true)).id();
        slug = "inst-" + sufixo;
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void limitesSomamPlanoEAdicionaisEVersaoSobeACadaMudanca() {
        ContratacaoResponse criada = contratar(new BigDecimal("2"));

        assertThat(criada.idempotencyKey()).isEqualTo(criada.id());
        assertThat(criada.versaoDireitos()).isEqualTo(1);
        assertThat(criada.direitos().versao()).isEqualTo(1);
        assertThat(criada.direitos().tenantId()).isNull();
        assertThat(criada.direitos().acessoLiberado()).isTrue();
        assertThat(criada.direitos().situacao()).isEqualTo("TRIAL");
        assertThat(criada.direitos().limites().get("VOLUNTARIOS")).isEqualByComparingTo("120");
        assertThat(criada.direitos().funcionalidades()).containsExactly("ESCALAS");
        assertThat(criada.historico()).extracting(HistoricoResponse::acao).containsExactly("CRIAR");
        assertThat(criada.historico().getFirst().operadorId()).isEqualTo(operadorId);

        ContratacaoResponse comMenos = contratacaoService.substituirAdicionais(criada.id(),
                new SubstituirAdicionaisRequest(List.of(new AdicionalContratadoRequest(adicionalId, BigDecimal.ONE)),
                        "tirou um pacote"));
        assertThat(comMenos.versaoDireitos()).isEqualTo(2);
        assertThat(comMenos.direitos().limites().get("VOLUNTARIOS")).isEqualByComparingTo("110");
        assertThat(comMenos.historico()).filteredOn(item -> "ADICIONAIS".equals(item.acao()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.motivo()).isEqualTo("tirou um pacote");
                    assertThat(item.operadorId()).isEqualTo(operadorId);
                });

        ContratacaoResponse bloqueada = contratacaoService.bloquear(criada.id(), "inadimplência longa");
        assertThat(bloqueada.versaoDireitos()).isEqualTo(3);
        assertThat(bloqueada.situacaoComercial()).isEqualTo(SituacaoComercial.BLOQUEADA);
        assertThat(bloqueada.direitos().acessoLiberado()).isFalse();
        assertThat(bloqueada.direitos().motivoBloqueio()).isEqualTo("inadimplência longa");

        List<EventoSaida> eventos = eventoRepository.findByContratacaoId(criada.id());
        assertThat(eventos).filteredOn(evento -> evento.getSituacao() == SituacaoEvento.PENDENTE)
                .singleElement()
                .satisfies(evento -> assertThat(evento.getVersao()).isEqualTo(3));
        assertThat(eventos).filteredOn(evento -> evento.getSituacao() == SituacaoEvento.DESCARTADO)
                .hasSize(2);
    }

    @Test
    void editarDadosDeProvisionamentoAntesDoEnvioMantemAChave() {
        ContratacaoResponse criada = contratar(BigDecimal.ONE);
        ContratacaoResponse editada = contratacaoService.atualizarProvisionamento(criada.id(),
                new AtualizarProvisionamentoRequest("Outro nome", slug + "-b", "Outro admin", "outro@teste.com"));

        assertThat(criada.provisionamentoEditavel()).isTrue();
        assertThat(editada.idempotencyKey()).isEqualTo(criada.id());
        assertThat(editada.slugInstancia()).isEqualTo(slug + "-b");
        assertThat(editada.provisionamentoEditavel()).isTrue();
        assertThat(editada.versaoDireitos()).isEqualTo(2);
        assertThat(editada.historico()).extracting(HistoricoResponse::acao)
                .contains("DADOS_PROVISIONAMENTO");
    }

    @Test
    void falhaDepoisDoBloqueioDesfazVersaoEventoEHistorico() {
        ContratacaoResponse criada = contratar(BigDecimal.ONE);
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> template.execute(status -> {
            contratacaoService.bloquear(criada.id(), "vai falhar");
            throw new IllegalStateException("falha de teste");
        })).hasMessageContaining("falha de teste");

        ContratacaoResponse atual = contratacaoService.buscar(criada.id());
        assertThat(atual.situacaoComercial()).isEqualTo(SituacaoComercial.TRIAL);
        assertThat(atual.versaoDireitos()).isEqualTo(1);
        assertThat(atual.historico()).extracting(HistoricoResponse::acao).containsExactly("CRIAR");
        assertThat(eventoRepository.findByContratacaoId(criada.id()))
                .singleElement()
                .satisfies(evento -> {
                    assertThat(evento.getSituacao()).isEqualTo(SituacaoEvento.PENDENTE);
                    assertThat(evento.getVersao()).isEqualTo(1);
                });
    }

    private ContratacaoResponse contratar(BigDecimal quantidadeAdicional) {
        return contratacaoService.criar(new CriarContratacaoRequest(
                clienteId, produtoId, planoId, Periodicidade.MENSAL, new BigDecimal("100.00"),
                10, LocalDate.now(), SituacaoComercial.TRIAL,
                "Instância " + slug, slug, "Admin", slug + "@teste.com", null,
                List.of(new AdicionalContratadoRequest(adicionalId, quantidadeAdicional))));
    }
}
