package br.com.central.api.comercial;

import br.com.central.api.AbstractIntegrationTest;
import br.com.central.api.Documentos;
import br.com.central.api.comercial.dto.CatalogoDtos.NovoPrecoRequest;
import br.com.central.api.comercial.dto.CatalogoDtos.PlanoResponse;
import br.com.central.api.comercial.dto.CatalogoDtos.SalvarAdicionalRequest;
import br.com.central.api.comercial.dto.CatalogoDtos.SalvarPlanoRequest;
import br.com.central.api.comercial.dto.CatalogoDtos.SalvarProdutoRequest;
import br.com.central.api.comercial.dto.CatalogoDtos.SalvarRecursoRequest;
import br.com.central.api.comercial.dto.ClienteDtos.SalvarClienteRequest;
import br.com.central.api.comercial.dto.ContratacaoDtos.AdicionalContratadoRequest;
import br.com.central.api.comercial.dto.ContratacaoDtos.AlterarPlanoRequest;
import br.com.central.api.comercial.dto.ContratacaoDtos.ContratacaoResponse;
import br.com.central.api.comercial.dto.ContratacaoDtos.CriarContratacaoRequest;
import br.com.central.api.comercial.dto.ContratacaoDtos.SubstituirAdicionaisRequest;
import br.com.central.api.comercial.dto.FinanceiroDtos.CobrancaDetalhe;
import br.com.central.api.comercial.dto.FinanceiroDtos.CobrancaItemResponse;
import br.com.central.api.comercial.dto.FinanceiroDtos.CobrancaLinha;
import br.com.central.api.comercial.dto.FinanceiroDtos.CobrancaResponse;
import br.com.central.api.comercial.dto.FinanceiroDtos.FiltroCobrancas;
import br.com.central.api.comercial.dto.FinanceiroDtos.FinanceiroResponse;
import br.com.central.api.comercial.dto.FinanceiroDtos.RegistrarPagamentoRequest;
import br.com.central.api.comercial.dto.FinanceiroDtos.SituacaoFinanceira;
import br.com.central.api.operador.Operador;
import br.com.central.api.operador.OperadorRepository;
import br.com.central.api.security.OperadorAutenticado;
import br.com.central.api.web.BadRequestException;
import br.com.central.api.web.ConflictException;
import br.com.central.api.web.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cobrança manual adaptada do Servire: a paróquia virou contratação.
 * Bloqueio manual não volta sozinho quando se quita; INADIMPLENTE volta para ATIVA.
 */
class BillingServiceIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal CEM = new BigDecimal("100.00");

    @Autowired
    private BillingService billingService;
    @Autowired
    private ContratacaoService contratacaoService;
    @Autowired
    private CatalogoService catalogoService;
    @Autowired
    private ClienteService clienteService;
    @Autowired
    private OperadorRepository operadorRepository;

    private LocalDate hoje;
    private UUID clienteId;
    private UUID produtoId;
    private PlanoResponse plano;

    @BeforeEach
    void preparar() {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        Operador operador = operadorRepository.saveAndFlush(
                new Operador("Operador " + sufixo, sufixo + "@central.test", "hash"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new OperadorAutenticado(operador.getId(), operador.getEmail()),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_OPERADOR"))));
        hoje = billingService.hoje();
        clienteId = clienteService.criar(new SalvarClienteRequest(
                TipoCliente.PJ, Documentos.cnpj(), "Cliente " + sufixo,
                null, null, null, null, null, null, null, null)).id();
        produtoId = catalogoService.criarProduto(new SalvarProdutoRequest(
                "P" + sufixo, "Produto " + sufixo, null, true)).id();
        plano = novoPlano();
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void assinaturaMensalGeraUmaCobrancaPorMesDesdeOInicioAteOProximoMes() {
        LocalDate inicio = hoje.minusMonths(2).withDayOfMonth(1);

        FinanceiroResponse financeiro = financeiro(contratar(Periodicidade.MENSAL, CEM, 10, inicio));

        assertThat(financeiro.cobrancas()).extracting(CobrancaResponse::competenciaInicio)
                .containsExactlyInAnyOrder(inicio, inicio.plusMonths(1), inicio.plusMonths(2), inicio.plusMonths(3));
        CobrancaResponse primeira = maisAntiga(financeiro);
        assertThat(primeira.competenciaFim()).isEqualTo(inicio.plusMonths(1).minusDays(1));
        assertThat(primeira.vencimento()).isEqualTo(inicio.withDayOfMonth(10));
        assertThat(primeira.valor()).isEqualByComparingTo(CEM);
        assertThat(primeira.vencida()).isTrue();
    }

    @Test
    void assinaturaAnualGeraUmaCobrancaPorPeriodoDeDozeMesesAPartirDoMesDeInicio() {
        LocalDate inicio = hoje.minusMonths(13);
        LocalDate mes = inicio.withDayOfMonth(1);

        FinanceiroResponse financeiro = financeiro(contratar(Periodicidade.ANUAL, new BigDecimal("1000.00"), 5, inicio));

        assertThat(financeiro.cobrancas()).extracting(CobrancaResponse::competenciaInicio)
                .containsExactlyInAnyOrder(mes, mes.plusMonths(12));
        assertThat(maisAntiga(financeiro).competenciaFim()).isEqualTo(mes.plusMonths(12).minusDays(1));
    }

    @Test
    void trimestralESemestralTemCompetenciaDeTresESeisMesesComecandoNoDiaUm() {
        LocalDate inicio = hoje.withDayOfMonth(15);
        LocalDate mes = inicio.withDayOfMonth(1);

        CobrancaResponse trimestral = maisAntiga(financeiro(contratar(Periodicidade.TRIMESTRAL, CEM, 10, inicio)));
        CobrancaResponse semestral = maisAntiga(financeiro(contratar(Periodicidade.SEMESTRAL, CEM, 10, inicio)));

        assertThat(trimestral.competenciaInicio()).isEqualTo(mes);
        assertThat(trimestral.competenciaFim()).isEqualTo(mes.plusMonths(3).minusDays(1));
        assertThat(semestral.competenciaFim()).isEqualTo(mes.plusMonths(6).minusDays(1));
    }

    @Test
    void adicionalEntraNaCobrancaComPrecoMensalVezesQuantidadeVezesMeses() {
        UUID adicionalId = novoAdicional(new BigDecimal("20.00"));

        ContratacaoResponse contratacao = contratar(Periodicidade.TRIMESTRAL, CEM, 10, hoje.withDayOfMonth(1),
                List.of(new AdicionalContratadoRequest(adicionalId, new BigDecimal("3"))));
        CobrancaResponse cobranca = maisAntiga(financeiro(contratacao));

        assertThat(cobranca.valor()).isEqualByComparingTo("280.00");
        CobrancaDetalhe detalhe = billingService.detalhe(cobranca.id());
        assertThat(detalhe.itens()).extracting(CobrancaItemResponse::tipo)
                .containsExactly(CobrancaItem.Tipo.PLANO, CobrancaItem.Tipo.ADICIONAL);
        assertThat(detalhe.itens().get(1).valor()).isEqualByComparingTo("180.00");
        assertThat(detalhe.itens().get(1).meses()).isEqualTo(3);
        assertThat(detalhe.cobranca().planoNome()).isEqualTo(plano.nome());
    }

    @Test
    void trocarAdicionaisRecalculaAsAbertasNaoVencidasEMantemAsVencidasEAsPagas() {
        UUID adicionalId = novoAdicional(new BigDecimal("20.00"));
        LocalDate inicio = hoje.minusMonths(2).withDayOfMonth(1);
        ContratacaoResponse contratacao = contratar(Periodicidade.MENSAL, CEM, 1, inicio);
        billingService.gerarAdiantadas(contratacao.id(), null, YearMonth.from(hoje).plusMonths(2));
        FinanceiroResponse antes = financeiro(contratacao);
        CobrancaResponse paga = maisAntiga(antes);
        pagar(contratacao.id(), paga.id());

        contratacaoService.substituirAdicionais(contratacao.id(), new SubstituirAdicionaisRequest(
                List.of(new AdicionalContratadoRequest(adicionalId, new BigDecimal("2"))), "pacote"));

        FinanceiroResponse depois = financeiro(contratacao);
        for (CobrancaResponse cobranca : depois.cobrancas()) {
            boolean recalculada = cobranca.status() == Cobranca.Status.ABERTA && !cobranca.vencimento().isBefore(hoje);
            assertThat(cobranca.valor()).as(cobranca.competenciaInicio().toString())
                    .isEqualByComparingTo(recalculada ? "140.00" : "100.00");
        }
        assertThat(depois.cobrancas()).anyMatch(CobrancaResponse::vencida);
        assertThat(depois.cobrancas()).anyMatch(cobranca -> cobranca.valor().compareTo(new BigDecimal("140.00")) == 0);
        assertThat(porId(depois, paga.id()).valorPago()).isEqualByComparingTo(CEM);
    }

    @Test
    void gerarComInicioEFimSoCriaAsQueFaltamNoIntervaloENaoMexeNasExistentes() {
        ContratacaoResponse contratacao = contratar(Periodicidade.MENSAL, CEM, 10, hoje.withDayOfMonth(1));
        FinanceiroResponse antes = financeiro(contratacao);
        YearMonth de = YearMonth.from(hoje).plusMonths(4);

        FinanceiroResponse depois = billingService.gerarAdiantadas(contratacao.id(), de, de.plusMonths(1));

        assertThat(depois.cobrancas()).hasSize(antes.cobrancas().size() + 2);
        assertThat(depois.cobrancas()).extracting(CobrancaResponse::competenciaInicio)
                .contains(de.atDay(1), de.plusMonths(1).atDay(1))
                .doesNotContain(de.minusMonths(1).atDay(1));
        for (CobrancaResponse existente : antes.cobrancas()) {
            assertThat(porId(depois, existente.id())).isEqualTo(existente);
        }
        assertThatThrownBy(() -> billingService.gerarAdiantadas(contratacao.id(), de, de.minusMonths(1)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void pagamentoPelaListaGeralQuitaCobrancasDeContratacoesDiferentes() {
        ContratacaoResponse primeira = contratar(Periodicidade.MENSAL, CEM, 1, hoje.minusMonths(1).withDayOfMonth(1));
        ContratacaoResponse segunda = contratar(Periodicidade.MENSAL, CEM, 10, hoje.plusMonths(1).withDayOfMonth(1));
        billingService.gerarCobrancasDeTodas();
        List<UUID> ids = List.of(maisAntiga(financeiro(primeira)).id(), maisAntiga(financeiro(segunda)).id());

        int pagas = billingService.registrarPagamentos(new RegistrarPagamentoRequest(
                ids, hoje, FormaPagamento.PIX, null, "lote"));

        assertThat(pagas).isEqualTo(2);
        assertThat(maisAntiga(financeiro(primeira)).status()).isEqualTo(Cobranca.Status.PAGA);
        assertThat(maisAntiga(financeiro(segunda)).status()).isEqualTo(Cobranca.Status.PAGA);
        assertThat(contratacaoService.buscar(segunda.id()).situacaoComercial()).isEqualTo(SituacaoComercial.ATIVA);
        assertThatThrownBy(() -> billingService.registrarPagamentos(new RegistrarPagamentoRequest(
                List.of(UUID.randomUUID()), hoje, FormaPagamento.PIX, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listaGeralFiltraPorProdutoSituacaoCompetenciaEBusca() {
        ContratacaoResponse atrasada = contratar(Periodicidade.MENSAL, CEM, 1, hoje.minusMonths(2).withDayOfMonth(1));
        ContratacaoResponse futura = contratar(Periodicidade.MENSAL, CEM, 10, hoje.plusMonths(1).withDayOfMonth(1));
        String nomeCliente = clienteService.buscar(clienteId).nome();

        List<CobrancaLinha> doProduto = billingService.listar(new FiltroCobrancas(
                produtoId, null, null, null, null, null, null));
        List<CobrancaLinha> vencidas = billingService.listar(new FiltroCobrancas(
                produtoId, "VENCIDA", null, null, null, null, null));
        List<CobrancaLinha> daCompetencia = billingService.listar(new FiltroCobrancas(
                produtoId, null, null, null, null, YearMonth.from(hoje).plusMonths(1), null));
        List<CobrancaLinha> porNome = billingService.listar(new FiltroCobrancas(
                null, null, null, null, null, null, nomeCliente.toUpperCase()));

        assertThat(doProduto).extracting(CobrancaLinha::contratacaoId).contains(atrasada.id(), futura.id());
        assertThat(vencidas).isNotEmpty().allMatch(CobrancaLinha::vencida)
                .extracting(CobrancaLinha::contratacaoId).containsOnly(atrasada.id());
        assertThat(daCompetencia).extracting(CobrancaLinha::contratacaoId).contains(atrasada.id(), futura.id());
        assertThat(daCompetencia).allMatch(linha -> linha.competenciaInicio().equals(hoje.plusMonths(1).withDayOfMonth(1)));
        assertThat(porNome).isNotEmpty().allMatch(linha -> linha.clienteNome().equals(nomeCliente));
        assertThatThrownBy(() -> billingService.listar(new FiltroCobrancas(
                null, "QUALQUER", null, null, null, null, null))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void mensalComInicioDepoisDoDiaDeVencimentoVenceNoProprioInicio() {
        LocalDate inicio = hoje.minusMonths(1).withDayOfMonth(20);

        FinanceiroResponse financeiro = financeiro(contratar(Periodicidade.MENSAL, CEM, 5, inicio));

        assertThat(maisAntiga(financeiro).vencimento()).isEqualTo(inicio);
    }

    @Test
    void abrirOFinanceiroDuasVezesNaoDuplicaCobrancas() {
        UUID id = contratar(Periodicidade.MENSAL, CEM, 10, hoje.minusMonths(3).withDayOfMonth(1)).id();
        int antes = billingService.financeiro(id).cobrancas().size();

        billingService.financeiro(id);
        billingService.gerarCobrancasDeTodas();

        assertThat(billingService.financeiro(id).cobrancas()).hasSize(antes);
    }

    @Test
    void valorOmitidoUsaOPrecoVigenteMaisRecenteQueJaComecou() {
        catalogoService.adicionarPreco(plano.id(), new NovoPrecoRequest(Periodicidade.MENSAL, CEM, hoje.minusDays(30)));
        catalogoService.adicionarPreco(plano.id(), new NovoPrecoRequest(
                Periodicidade.MENSAL, new BigDecimal("150.00"), hoje.minusDays(1)));
        catalogoService.adicionarPreco(plano.id(), new NovoPrecoRequest(
                Periodicidade.MENSAL, new BigDecimal("200.00"), hoje.plusDays(10)));

        ContratacaoResponse contratacao = contratar(Periodicidade.MENSAL, null, 10, hoje.withDayOfMonth(1));

        assertThat(contratacao.valor()).isEqualByComparingTo("150.00");
        assertThat(catalogoService.listarPlanos(produtoId)).filteredOn(item -> item.id().equals(plano.id()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.precoMensal()).isEqualByComparingTo("150.00");
                    assertThat(item.precoAnual()).isNull();
                    assertThat(item.precos()).hasSize(3);
                });
    }

    @Test
    void semPrecoNoCatalogoESemValorInformadoLancaBadRequest() {
        assertThatThrownBy(() -> contratar(Periodicidade.ANUAL, null, 10, hoje))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void precoDuplicadoNaMesmaDataLancaConflict() {
        catalogoService.adicionarPreco(plano.id(), new NovoPrecoRequest(Periodicidade.MENSAL, CEM, hoje));

        assertThatThrownBy(() -> catalogoService.adicionarPreco(plano.id(),
                new NovoPrecoRequest(Periodicidade.MENSAL, new BigDecimal("120.00"), hoje)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void registrarPagamentoMarcaPagaAtivaTrialEEstendeVigencia() {
        LocalDate proximoMes = hoje.plusMonths(1).withDayOfMonth(1);
        ContratacaoResponse contratacao = contratar(Periodicidade.MENSAL, CEM, 10, proximoMes);
        CobrancaResponse cobranca = maisAntiga(financeiro(contratacao));

        FinanceiroResponse depois = billingService.registrarPagamento(contratacao.id(), new RegistrarPagamentoRequest(
                List.of(cobranca.id()), hoje, FormaPagamento.PIX, null, "PIX conferido"));

        CobrancaResponse paga = porId(depois, cobranca.id());
        assertThat(paga.status()).isEqualTo(Cobranca.Status.PAGA);
        assertThat(paga.formaPagamento()).isEqualTo(FormaPagamento.PIX);
        assertThat(paga.pagoEm()).isEqualTo(hoje);
        assertThat(paga.valorPago()).isEqualByComparingTo(CEM);
        assertThat(paga.observacao()).isEqualTo("PIX conferido");
        assertThat(depois.resumo().totalPagoNoAno()).isEqualByComparingTo(CEM);

        ContratacaoResponse atual = contratacaoService.buscar(contratacao.id());
        assertThat(atual.situacaoComercial()).isEqualTo(SituacaoComercial.ATIVA);
        assertThat(atual.vigenteAte()).isEqualTo(cobranca.competenciaFim());
        assertThat(atual.direitos().acessoLiberado()).isTrue();
        assertThat(atual.historico()).anyMatch(item -> "PAGAMENTO".equals(item.acao()));
    }

    @Test
    void pagamentoParcialMantemInadimplenteEQuitarTudoVoltaParaAtiva() {
        ContratacaoResponse contratacao = contratar(Periodicidade.MENSAL, CEM, 1, hoje.minusMonths(3).withDayOfMonth(1));
        billingService.gerarCobrancasDeTodas();
        FinanceiroResponse financeiro = billingService.financeiro(contratacao.id());
        List<CobrancaResponse> vencidas = financeiro.cobrancas().stream()
                .filter(CobrancaResponse::vencida)
                .sorted(Comparator.comparing(CobrancaResponse::competenciaInicio))
                .toList();
        assertThat(vencidas).hasSizeGreaterThanOrEqualTo(3);
        assertThat(financeiro.resumo().diasAtraso()).isPositive();
        assertThat(contratacaoService.buscar(contratacao.id()).situacaoComercial())
                .isEqualTo(SituacaoComercial.INADIMPLENTE);

        billingService.registrarPagamento(contratacao.id(), new RegistrarPagamentoRequest(
                List.of(vencidas.getFirst().id()), hoje, FormaPagamento.DINHEIRO, null, null));
        assertThat(contratacaoService.buscar(contratacao.id()).situacaoComercial())
                .isEqualTo(SituacaoComercial.INADIMPLENTE);

        FinanceiroResponse quitado = billingService.registrarPagamento(contratacao.id(), new RegistrarPagamentoRequest(
                vencidas.subList(1, vencidas.size()).stream().map(CobrancaResponse::id).toList(),
                hoje, FormaPagamento.CARTAO_CREDITO, null, null));

        assertThat(quitado.resumo().vencidas()).isZero();
        assertThat(quitado.resumo().diasAtraso()).isZero();
        ContratacaoResponse atual = contratacaoService.buscar(contratacao.id());
        assertThat(atual.situacaoComercial()).isEqualTo(SituacaoComercial.ATIVA);
        assertThat(atual.direitos().acessoLiberado()).isTrue();
    }

    @Test
    void pagamentoNaoDesbloqueiaContratacaoBloqueada() {
        ContratacaoResponse contratacao = contratar(Periodicidade.MENSAL, CEM, 1, hoje.minusMonths(2).withDayOfMonth(1));
        contratacaoService.bloquear(contratacao.id(), "pedido do cliente");
        List<UUID> vencidas = billingService.financeiro(contratacao.id()).cobrancas().stream()
                .filter(CobrancaResponse::vencida).map(CobrancaResponse::id).toList();

        billingService.registrarPagamento(contratacao.id(), new RegistrarPagamentoRequest(
                vencidas, hoje, FormaPagamento.PIX, null, null));

        ContratacaoResponse atual = contratacaoService.buscar(contratacao.id());
        assertThat(atual.situacaoComercial()).isEqualTo(SituacaoComercial.BLOQUEADA);
        assertThat(atual.direitos().acessoLiberado()).isFalse();
    }

    @Test
    void jobNaoRepeteAVersaoSeJaEstaInadimplente() {
        ContratacaoResponse contratacao = contratar(Periodicidade.MENSAL, CEM, 1, hoje.minusMonths(2).withDayOfMonth(1));
        billingService.gerarCobrancasDeTodas();
        int versao = contratacaoService.buscar(contratacao.id()).versaoDireitos();

        billingService.gerarCobrancasDeTodas();

        ContratacaoResponse atual = contratacaoService.buscar(contratacao.id());
        assertThat(atual.situacaoComercial()).isEqualTo(SituacaoComercial.INADIMPLENTE);
        assertThat(atual.versaoDireitos()).isEqualTo(versao);
        assertThat(atual.direitos().acessoLiberado()).isTrue();
    }

    @Test
    void valorPagoInformadoComMaisDeUmaCobrancaLancaBadRequest() {
        ContratacaoResponse contratacao = contratar(Periodicidade.MENSAL, CEM, 10, hoje.minusMonths(1).withDayOfMonth(1));
        List<UUID> ids = financeiro(contratacao).cobrancas().stream().map(CobrancaResponse::id).toList();

        assertThatThrownBy(() -> billingService.registrarPagamento(contratacao.id(), new RegistrarPagamentoRequest(
                ids, hoje, FormaPagamento.PIX, new BigDecimal("50.00"), null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void pagamentoComDataNoFuturoLancaBadRequest() {
        ContratacaoResponse contratacao = contratar(Periodicidade.MENSAL, CEM, 10, hoje.withDayOfMonth(1));
        CobrancaResponse cobranca = maisAntiga(financeiro(contratacao));

        assertThatThrownBy(() -> billingService.registrarPagamento(contratacao.id(), new RegistrarPagamentoRequest(
                List.of(cobranca.id()), hoje.plusDays(1), FormaPagamento.PIX, null, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void pagarCobrancaJaPagaOuIsentaLancaBadRequest() {
        ContratacaoResponse contratacao = contratar(Periodicidade.MENSAL, CEM, 10, hoje.minusMonths(1).withDayOfMonth(1));
        FinanceiroResponse financeiro = financeiro(contratacao);
        CobrancaResponse primeira = maisAntiga(financeiro);
        CobrancaResponse outra = financeiro.cobrancas().stream()
                .filter(cobranca -> !cobranca.id().equals(primeira.id())).findFirst().orElseThrow();
        pagar(contratacao.id(), primeira.id());
        billingService.isentar(contratacao.id(), outra.id(), "Cortesia");

        assertThatThrownBy(() -> pagar(contratacao.id(), primeira.id())).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> pagar(contratacao.id(), outra.id())).isInstanceOf(BadRequestException.class);
        assertThat(porId(billingService.financeiro(contratacao.id()), outra.id()).observacao()).isEqualTo("Cortesia");
    }

    @Test
    void cobrancaDeOutraContratacaoDaNotFound() {
        ContratacaoResponse contratacao = contratar(Periodicidade.MENSAL, CEM, 10, hoje.withDayOfMonth(1));
        CobrancaResponse cobranca = maisAntiga(financeiro(contratacao));
        UUID outra = contratar(Periodicidade.MENSAL, CEM, 10, hoje.withDayOfMonth(1)).id();

        assertThatThrownBy(() -> billingService.registrarPagamento(outra, new RegistrarPagamentoRequest(
                List.of(cobranca.id()), hoje, FormaPagamento.PIX, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> billingService.estornar(outra, cobranca.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void estornarVoltaACobrancaParaAberta() {
        ContratacaoResponse contratacao = contratar(Periodicidade.MENSAL, CEM, 10, hoje.withDayOfMonth(1));
        CobrancaResponse cobranca = maisAntiga(financeiro(contratacao));
        pagar(contratacao.id(), cobranca.id());

        FinanceiroResponse depois = billingService.estornar(contratacao.id(), cobranca.id());

        CobrancaResponse estornada = porId(depois, cobranca.id());
        assertThat(estornada.status()).isEqualTo(Cobranca.Status.ABERTA);
        assertThat(estornada.pagoEm()).isNull();
        assertThat(estornada.formaPagamento()).isNull();
        assertThatThrownBy(() -> billingService.estornar(contratacao.id(), cobranca.id()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void trocarDePlanoMantemOPagoERecalculaAsAbertasDaqueleDiaEmDiante() {
        LocalDate inicio = hoje.minusMonths(2).withDayOfMonth(1);
        ContratacaoResponse antes = contratar(Periodicidade.MENSAL, CEM, 10, inicio);
        pagar(antes.id(), maisAntiga(financeiro(antes)).id());
        LocalDate proximoMes = hoje.plusMonths(1).withDayOfMonth(1);
        PlanoResponse novo = novoPlano();

        contratacaoService.alterarPlano(antes.id(), new AlterarPlanoRequest(
                novo.id(), Periodicidade.MENSAL, new BigDecimal("180.00"), 10, proximoMes, "reajuste"));
        FinanceiroResponse depois = billingService.financeiro(antes.id());

        assertThat(contratacaoService.buscar(antes.id()).planoId()).isEqualTo(novo.id());
        assertThat(depois.cobrancas()).filteredOn(cobranca -> cobranca.competenciaInicio().equals(inicio))
                .extracting(CobrancaResponse::status).containsExactly(Cobranca.Status.PAGA);
        assertThat(depois.cobrancas()).filteredOn(cobranca -> cobranca.competenciaInicio().equals(hoje.withDayOfMonth(1)))
                .singleElement()
                .satisfies(cobranca -> {
                    assertThat(cobranca.status()).isEqualTo(Cobranca.Status.ABERTA);
                    assertThat(cobranca.valor()).isEqualByComparingTo(CEM);
                });
        assertThat(depois.cobrancas()).filteredOn(cobranca -> cobranca.competenciaInicio().equals(proximoMes))
                .singleElement()
                .satisfies(cobranca -> {
                    assertThat(cobranca.status()).isEqualTo(Cobranca.Status.ABERTA);
                    assertThat(cobranca.valor()).isEqualByComparingTo("180.00");
                });
    }

    @Test
    void contratacaoCanceladaNaoRecebePagamento() {
        ContratacaoResponse contratacao = contratar(Periodicidade.MENSAL, CEM, 10, hoje);
        contratacaoService.cancelar(contratacao.id(), "encerrou");
        CobrancaResponse cobranca = maisAntiga(billingService.financeiro(contratacao.id()));

        assertThatThrownBy(() -> pagar(contratacao.id(), cobranca.id())).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> contratacaoService.alterarPlano(contratacao.id(), new AlterarPlanoRequest(
                plano.id(), Periodicidade.MENSAL, CEM, 10, hoje, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void gerarAdiantadasCriaOsMesesPedidosERespeitaOLimite() {
        ContratacaoResponse contratacao = contratar(Periodicidade.MENSAL, CEM, 10, hoje.withDayOfMonth(1));
        YearMonth ate = YearMonth.from(hoje).plusMonths(5);

        FinanceiroResponse financeiro = billingService.gerarAdiantadas(contratacao.id(), null, ate);

        assertThat(financeiro.cobrancas()).hasSize(6);
        assertThatThrownBy(() -> billingService.gerarAdiantadas(
                contratacao.id(), null, YearMonth.from(hoje).plusMonths(BillingService.MAX_MESES_ADIANTADOS + 1)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void situacaoMostraPlanoEDiasDeAtraso() {
        ContratacaoResponse atrasada = contratar(Periodicidade.MENSAL, CEM, 1, hoje.minusMonths(2).withDayOfMonth(1));
        ContratacaoResponse emDia = contratar(Periodicidade.MENSAL, CEM, 10, hoje.plusMonths(1).withDayOfMonth(1));

        Map<UUID, SituacaoFinanceira> situacao = billingService.situacaoPorContratacao(
                List.of(atrasada.id(), emDia.id()));

        assertThat(situacao.get(atrasada.id()).planoNome()).isEqualTo(plano.nome());
        assertThat(situacao.get(atrasada.id()).cobrancasVencidas()).isGreaterThanOrEqualTo(2);
        assertThat(situacao.get(atrasada.id()).diasAtraso()).isPositive();
        assertThat(situacao.get(emDia.id()).cobrancasVencidas()).isZero();
    }

    @Test
    void recebidoNoMesSomaOValorPago() {
        BigDecimal antes = billingService.recebidoNoMes();
        ContratacaoResponse contratacao = contratar(Periodicidade.MENSAL, CEM, 10, hoje.withDayOfMonth(1));
        CobrancaResponse cobranca = maisAntiga(financeiro(contratacao));

        billingService.registrarPagamento(contratacao.id(), new RegistrarPagamentoRequest(
                List.of(cobranca.id()), hoje, FormaPagamento.PIX, new BigDecimal("90.00"), "desconto"));

        assertThat(billingService.recebidoNoMes()).isEqualByComparingTo(antes.add(new BigDecimal("90.00")));
    }

    private FinanceiroResponse financeiro(ContratacaoResponse contratacao) {
        return billingService.financeiro(contratacao.id());
    }

    private void pagar(UUID contratacaoId, UUID cobrancaId) {
        billingService.registrarPagamento(contratacaoId, new RegistrarPagamentoRequest(
                List.of(cobrancaId), hoje, FormaPagamento.PIX, null, null));
    }

    private ContratacaoResponse contratar(Periodicidade periodicidade, BigDecimal valor, int dia, LocalDate inicio) {
        return contratar(periodicidade, valor, dia, inicio, null);
    }

    private ContratacaoResponse contratar(Periodicidade periodicidade, BigDecimal valor, int dia, LocalDate inicio,
                                          List<AdicionalContratadoRequest> adicionais) {
        String slug = "fin-" + UUID.randomUUID().toString().substring(0, 8);
        return contratacaoService.criar(new CriarContratacaoRequest(
                clienteId, produtoId, plano.id(), periodicidade, valor, dia, inicio, SituacaoComercial.TRIAL,
                "Instância " + slug, slug, "Admin", slug + "@teste.com", null, adicionais));
    }

    private UUID novoAdicional(BigDecimal precoMensal) {
        String codigo = "R" + UUID.randomUUID().toString().substring(0, 8);
        UUID recursoId = catalogoService.criarRecurso(new SalvarRecursoRequest(
                produtoId, codigo, "Voluntários", TipoRecurso.LIMITE, "pessoa")).id();
        return catalogoService.criarAdicional(new SalvarAdicionalRequest(
                produtoId, recursoId, "A" + codigo, "Mais 5 voluntários", new BigDecimal("5"), precoMensal, true)).id();
    }

    private static CobrancaResponse maisAntiga(FinanceiroResponse financeiro) {
        return financeiro.cobrancas().stream()
                .min(Comparator.comparing(CobrancaResponse::competenciaInicio)).orElseThrow();
    }

    private static CobrancaResponse porId(FinanceiroResponse financeiro, UUID id) {
        return financeiro.cobrancas().stream().filter(cobranca -> cobranca.id().equals(id)).findFirst().orElseThrow();
    }

    private PlanoResponse novoPlano() {
        String codigo = "T" + UUID.randomUUID().toString().substring(0, 8);
        return catalogoService.criarPlano(new SalvarPlanoRequest(produtoId, codigo, "Plano " + codigo, true, null));
    }
}
