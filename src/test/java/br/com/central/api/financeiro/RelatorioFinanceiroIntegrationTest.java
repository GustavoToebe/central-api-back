package br.com.central.api.financeiro;

import br.com.central.api.AbstractIntegrationTest;
import br.com.central.api.comercial.Cobranca;
import br.com.central.api.comercial.CobrancaRepository;
import br.com.central.api.web.BadRequestException;
import br.com.central.api.web.ResourceNotFoundException;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static br.com.central.api.financeiro.FinanceiroDtos.*;
import static br.com.central.api.financeiro.MovimentoFinanceiro.Tipo;
import static br.com.central.api.financeiro.RelatorioFinanceiroDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class RelatorioFinanceiroIntegrationTest extends AbstractIntegrationTest {
    @Autowired FinanceiroService financeiro;
    @Autowired RelatorioFinanceiroService relatorios;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean CobrancaRepository cobrancas;

    private final LocalDate hoje = LocalDate.of(1905, 1, 15);
    private ContaResponse caixa;
    private CategoriaResponse doacoes, infra, servicos;

    @BeforeEach void preparar() {
        jdbc.update("delete from financeiro_movimento"); jdbc.update("delete from financeiro_conta"); jdbc.update("delete from financeiro_categoria");
        Mockito.reset(cobrancas);
        when(cobrancas.somarRecebido(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(cobrancas.somarPrevisto(any(), any())).thenReturn(BigDecimal.ZERO);
        caixa = financeiro.salvarConta(null, new ContaRequest("Caixa", new BigDecimal("100.00"), LocalDate.of(1905, 1, 1), true));
        var receitas = financeiro.salvarCategoria(null, new CategoriaRequest("Receitas", true, Tipo.RECEITA, null));
        var despesas = financeiro.salvarCategoria(null, new CategoriaRequest("Despesas", true, Tipo.DESPESA, null));
        doacoes = financeiro.salvarCategoria(null, new CategoriaRequest("Doações", true, Tipo.RECEITA, receitas.id()));
        infra = financeiro.salvarCategoria(null, new CategoriaRequest("Infraestrutura", true, Tipo.DESPESA, despesas.id()));
        servicos = financeiro.salvarCategoria(null, new CategoriaRequest("Serviços", true, Tipo.DESPESA, despesas.id()));
    }

    private MovimentoResponse lancar(String descricao, Tipo tipo, String valor, CategoriaResponse conta, LocalDate vencimento, LocalDate baixa) {
        var m = financeiro.salvarMovimento(null, new MovimentoRequest(descricao, tipo, new BigDecimal(valor), vencimento, caixa.id(), conta.id(), null, 0L));
        return baixa == null ? m : financeiro.baixar(m.id(), new BaixaRequest(baixa, m.versao()));
    }

    private UsernamePasswordAuthenticationToken usuario(String... permissoes) {
        return UsernamePasswordAuthenticationToken.authenticated("operador-teste", null, java.util.Arrays.stream(permissoes).map(SimpleGrantedAuthority::new).toList());
    }

    @Test void despesasAgrupamPorGrupoEContaComTotaisELancamentos() {
        lancar("VPS", Tipo.DESPESA, "20", infra, hoje, hoje);
        lancar("Supabase", Tipo.DESPESA, "30", infra, hoje, hoje);
        lancar("Resend", Tipo.DESPESA, "5", servicos, hoje, hoje);
        lancar("Receita não entra", Tipo.RECEITA, "99", doacoes, hoje, hoje);
        var r = relatorios.porTipo(Tipo.DESPESA, Visao.REALIZADO, hoje, hoje);
        assertThat(r.total()).isEqualByComparingTo("55");
        assertThat(r.grupos()).hasSize(1);
        var grupo = r.grupos().getFirst();
        assertThat(grupo.nome()).isEqualTo("Despesas");
        assertThat(grupo.total()).isEqualByComparingTo("55");
        assertThat(grupo.contas()).extracting(ContaContabilLinha::nome).containsExactly("Infraestrutura", "Serviços");
        assertThat(grupo.contas().getFirst().total()).isEqualByComparingTo("50");
        assertThat(grupo.contas().getFirst().lancamentos()).extracting(Lancamento::descricao).containsExactly("Supabase", "VPS");
        assertThat(grupo.contas().getFirst().lancamentos().getFirst().contaBanco()).isEqualTo("Caixa");
    }

    @Test void lancamentoAntigoEmContaDeOutroTipoApareceSinalizado() {
        var m = lancar("Entrada antiga", Tipo.RECEITA, "10", doacoes, hoje, hoje);
        jdbc.update("update financeiro_movimento set tipo = 'DESPESA' where id = ?", m.id());
        var r = relatorios.porTipo(Tipo.DESPESA, Visao.REALIZADO, hoje, hoje);
        var conta = r.grupos().stream().flatMap(g -> g.contas().stream()).findFirst().orElseThrow();
        assertThat(conta.nome()).isEqualTo("Doações (conta cadastrada como entrada)");
        assertThat(r.grupos().getFirst().nome()).isEqualTo("Receitas (grupo de entrada)");
        assertThat(r.total()).isEqualByComparingTo("10");
    }

    @Test void visaoPrevistaMostraPendentesPeloVencimentoEARealizadaSoBaixados() {
        lancar("Pendente", Tipo.DESPESA, "40", infra, hoje, null);
        assertThat(relatorios.porTipo(Tipo.DESPESA, Visao.REALIZADO, hoje, hoje).total()).isEqualByComparingTo("0");
        var previsto = relatorios.porTipo(Tipo.DESPESA, Visao.PREVISTO, hoje, hoje);
        assertThat(previsto.total()).isEqualByComparingTo("40");
        assertThat(previsto.grupos().getFirst().contas().getFirst().lancamentos().getFirst().data()).isEqualTo(hoje);
    }

    @Test void periodoDaVisaoRealizadaUsaADataDaBaixaEnaoOVencimento() {
        lancar("Venceu em janeiro, pago em fevereiro", Tipo.DESPESA, "10", infra, hoje, LocalDate.of(1905, 2, 1));
        assertThat(relatorios.porTipo(Tipo.DESPESA, Visao.REALIZADO, hoje, hoje).total()).isEqualByComparingTo("0");
        assertThat(relatorios.porTipo(Tipo.DESPESA, Visao.REALIZADO, LocalDate.of(1905, 2, 1), LocalDate.of(1905, 2, 1)).total()).isEqualByComparingTo("10");
    }

    @Test void receitasIncluemAsCobrancasDeAssinaturaPagasEmUmGrupoCalculado() {
        lancar("Doação", Tipo.RECEITA, "10", doacoes, hoje, hoje);
        var cobranca = Mockito.mock(Cobranca.class);
        when(cobranca.getId()).thenReturn(UUID.randomUUID());
        when(cobranca.getSequencial()).thenReturn(7L);
        when(cobranca.getPagoEm()).thenReturn(hoje);
        when(cobranca.getValorPago()).thenReturn(new BigDecimal("150.00"));
        when(cobrancas.pagasNoPeriodo(any(), any(), any())).thenReturn(List.of(cobranca));
        var r = relatorios.porTipo(Tipo.RECEITA, Visao.REALIZADO, hoje, hoje);
        assertThat(r.total()).isEqualByComparingTo("160");
        var comercial = r.grupos().stream().filter(GrupoLinha::comercial).findFirst().orElseThrow();
        assertThat(comercial.nome()).isEqualTo("Assinaturas dos aplicativos");
        assertThat(comercial.contas().getFirst().lancamentos().getFirst().descricao()).isEqualTo("Cobrança nº 7");
        assertThat(relatorios.porTipo(Tipo.DESPESA, Visao.REALIZADO, hoje, hoje).grupos()).isEmpty();
    }

    @Test void bancoCaixaMostraSaldoAnteriorEntradasSaidasESaldoCorrido() {
        lancar("Antes do período", Tipo.RECEITA, "50", doacoes, hoje, LocalDate.of(1905, 1, 10));
        lancar("Entrada", Tipo.RECEITA, "30", doacoes, hoje, hoje);
        lancar("Saída", Tipo.DESPESA, "20", infra, hoje, hoje.plusDays(1));
        var r = relatorios.bancoCaixa(hoje, hoje.plusDays(5), null);
        assertThat(r.contas()).hasSize(1);
        var c = r.contas().getFirst();
        assertThat(c.saldoAnterior()).isEqualByComparingTo("150");
        assertThat(c.entradas()).isEqualByComparingTo("30");
        assertThat(c.saidas()).isEqualByComparingTo("20");
        assertThat(c.saldoFinal()).isEqualByComparingTo("160");
        assertThat(c.movimentos()).extracting(MovimentoBanco::saldo).usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("180"), new BigDecimal("160"));
        assertThat(r.saldoFinal()).isEqualByComparingTo(financeiro.resumo(hoje, hoje.plusDays(5)).saldoTotal());
        assertThat(r.observacao()).contains("Cobranças de assinaturas");
    }

    @Test void saldoInicialDentroDoPeriodoApareceComoLinhaDeAberturaESomaNoSaldoFinal() {
        financeiro.salvarConta(null, new ContaRequest("Banco novo", new BigDecimal("70.00"), hoje.plusDays(1), true));
        var r = relatorios.bancoCaixa(hoje, hoje.plusDays(5), null);
        var banco = r.contas().stream().filter(c -> c.nome().equals("Banco novo")).findFirst().orElseThrow();
        assertThat(banco.saldoAnterior()).isEqualByComparingTo("0");
        assertThat(banco.movimentos()).hasSize(1);
        assertThat(banco.movimentos().getFirst().descricao()).isEqualTo("Saldo inicial da conta");
        assertThat(banco.saldoFinal()).isEqualByComparingTo("70");
        assertThat(r.saldoFinal()).isEqualByComparingTo(financeiro.resumo(hoje, hoje.plusDays(5)).saldoTotal());
    }

    @Test void bancoCaixaFiltraPorContaEContaInexistenteDa404() {
        financeiro.salvarConta(null, new ContaRequest("Outra", BigDecimal.ZERO, LocalDate.of(1905, 1, 1), true));
        assertThat(relatorios.bancoCaixa(hoje, hoje, caixa.id()).contas()).extracting(ContaBanco::nome).containsExactly("Caixa");
        assertThat(relatorios.bancoCaixa(hoje, hoje, null).contas()).hasSize(2);
        assertThatThrownBy(() -> relatorios.bancoCaixa(hoje, hoje, UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void demonstrativoCalculaResultadoPrevistoESaldosSemDetalharLancamentos() {
        lancar("Doação", Tipo.RECEITA, "100", doacoes, hoje, hoje);
        lancar("VPS", Tipo.DESPESA, "30", infra, hoje, hoje);
        lancar("A receber", Tipo.RECEITA, "15", doacoes, hoje, null);
        lancar("A pagar", Tipo.DESPESA, "8", servicos, hoje, null);
        var d = relatorios.demonstrativo(hoje, hoje);
        assertThat(d.totalReceitas()).isEqualByComparingTo("100");
        assertThat(d.totalDespesas()).isEqualByComparingTo("30");
        assertThat(d.resultado()).isEqualByComparingTo("70");
        assertThat(d.aReceber()).isEqualByComparingTo("15");
        assertThat(d.aPagar()).isEqualByComparingTo("8");
        assertThat(d.resultadoPrevisto()).isEqualByComparingTo("77");
        assertThat(d.saldos()).extracting(SaldoConta::nome).containsExactly("Caixa");
        assertThat(d.receitas().grupos().getFirst().contas().getFirst().lancamentos()).isEmpty();
    }

    @Test void periodoInvalidoELimiteDeTamanho() {
        assertThatThrownBy(() -> relatorios.porTipo(Tipo.DESPESA, Visao.REALIZADO, hoje, hoje.minusDays(1))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> relatorios.bancoCaixa(LocalDate.of(1800, 1, 1), hoje, null)).isInstanceOf(BadRequestException.class);
    }

    @Test void apenasOOperadorLeOsRelatorios() throws Exception {
        lancar("VPS", Tipo.DESPESA, "30", infra, hoje, hoje);
        for (String rota : List.of("despesas", "receitas", "banco-caixa", "demonstrativo")) {
            mvc.perform(get("/financeiro/relatorios/" + rota).param("de", hoje.toString()).param("ate", hoje.toString())
                    .with(authentication(usuario("PERM_INTEGRACAO")))).andExpect(status().isForbidden());
            mvc.perform(get("/financeiro/relatorios/" + rota).param("de", hoje.toString()).param("ate", hoje.toString())
                    .with(authentication(usuario("ROLE_OPERADOR")))).andExpect(status().isOk());
        }
        mvc.perform(get("/financeiro/relatorios/despesas").param("de", hoje.toString()).param("ate", hoje.toString())
                .with(authentication(usuario("ROLE_OPERADOR")))).andExpect(jsonPath("$.total").value(30)).andExpect(jsonPath("$.grupos[0].contas[0].nome").value("Infraestrutura"));
        mvc.perform(get("/financeiro/relatorios/despesas").param("de", hoje.toString()).param("ate", hoje.minusDays(1).toString())
                .with(authentication(usuario("ROLE_OPERADOR")))).andExpect(status().isBadRequest());
    }
}
