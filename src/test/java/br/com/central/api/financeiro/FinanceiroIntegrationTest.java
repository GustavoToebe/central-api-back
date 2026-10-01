package br.com.central.api.financeiro;

import br.com.central.api.AbstractIntegrationTest;
import br.com.central.api.web.ConflictException;
import br.com.central.api.web.ResourceNotFoundException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static br.com.central.api.financeiro.FinanceiroDtos.*;
import static br.com.central.api.financeiro.MovimentoFinanceiro.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class FinanceiroIntegrationTest extends AbstractIntegrationTest {
    @Autowired FinanceiroService financeiro;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager tx;
    private ContaResponse conta;
    private CategoriaResponse categoria;
    private final LocalDate hoje = LocalDate.of(1905,1,15);
    @BeforeEach void preparar() {
        jdbc.update("delete from financeiro_movimento"); jdbc.update("delete from financeiro_conta"); jdbc.update("delete from financeiro_categoria");
        conta = financeiro.salvarConta(null,new ContaRequest("Caixa", new BigDecimal("100.00"), hoje.minusDays(10),true));
        categoria = financeiro.salvarCategoria(null,new CategoriaRequest("Doações",true));
    }
    private MovimentoResponse movimento(Tipo tipo, String valor) {
        return financeiro.salvarMovimento(null,new MovimentoRequest("Teste",tipo,new BigDecimal(valor),hoje,conta.id(),categoria.id(),null,0L));
    }
    @Test void saldoUsaSomenteBaixasEEstornoReverteUmaUnicaVez() {
        var entrada=movimento(Tipo.RECEITA,"50.00"); var saida=movimento(Tipo.DESPESA,"20.00");
        assertThat(financeiro.resumo(hoje,hoje).saldoTotal()).isEqualByComparingTo("100.00");
        entrada=financeiro.baixar(entrada.id(),new BaixaRequest(hoje,entrada.versao()));
        saida=financeiro.baixar(saida.id(),new BaixaRequest(hoje,saida.versao()));
        var resumo=financeiro.resumo(hoje,hoje);
        assertThat(resumo.receitas()).isEqualByComparingTo("50.00"); assertThat(resumo.despesas()).isEqualByComparingTo("20.00");
        assertThat(resumo.resultado()).isEqualByComparingTo("30.00"); assertThat(resumo.saldoTotal()).isEqualByComparingTo("130.00");
        financeiro.estornar(saida.id(),new VersaoRequest(saida.versao()));
        assertThat(financeiro.resumo(hoje,hoje).saldoTotal()).isEqualByComparingTo("150.00");
        var estornada=saida;
        assertThatThrownBy(() -> financeiro.estornar(estornada.id(),new VersaoRequest(estornada.versao()))).isInstanceOf(ConflictException.class);
    }
    @Test void periodoUsaDataPagamentoEnquantoListaUsaVencimento() {
        var m=movimento(Tipo.RECEITA,"30"); financeiro.baixar(m.id(),new BaixaRequest(hoje.minusDays(1),m.versao()));
        assertThat(financeiro.resumo(hoje,hoje).receitas()).isEqualByComparingTo("0");
        assertThat(financeiro.resumo(hoje,hoje).saldoTotal()).isEqualByComparingTo("130");
        assertThat(financeiro.listar(hoje,hoje,null,null,null,null,null,0,30).total()).isEqualTo(1);
    }


    @Test void saldoInicialProtegidoAceitaMesmoValorComEscalaDiferente() {
        movimento(Tipo.RECEITA,"1");
        financeiro.salvarConta(conta.id(),new ContaRequest("Caixa renomeado",new BigDecimal("100"),conta.dataSaldoInicial(),true));
        assertThatThrownBy(() -> financeiro.salvarConta(conta.id(),new ContaRequest("Caixa",new BigDecimal("200"),conta.dataSaldoInicial(),true))).isInstanceOf(ConflictException.class);
    }
    @Test void canceladoNaoPodeReceberBaixaENaoAlteraSaldo() {
        var m=movimento(Tipo.DESPESA,"10"); var c=financeiro.cancelar(m.id(),new VersaoRequest(m.versao()));
        assertThatThrownBy(() -> financeiro.baixar(c.id(),new BaixaRequest(hoje,c.versao()))).isInstanceOf(ConflictException.class);
        assertThat(financeiro.resumo(hoje,hoje).saldoTotal()).isEqualByComparingTo("100");
    }
    @Test void duasBaixasSimultaneasContamValorUmaUnicaVez() throws Exception {
        var m=movimento(Tipo.RECEITA,"50"); var inicio=new CountDownLatch(1);
        try (var executor=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> tarefa=() -> {
                try { inicio.await(5,TimeUnit.SECONDS); financeiro.baixar(m.id(),new BaixaRequest(hoje,m.versao())); return true; }
                catch (ConflictException ex) { return false; }
            };
            var a=executor.submit(tarefa); var b=executor.submit(tarefa); inicio.countDown();
            assertThat(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(financeiro.resumo(hoje,hoje).saldoTotal()).isEqualByComparingTo("150");
    }
    private UsernamePasswordAuthenticationToken usuario(String... permissoes) {
        return UsernamePasswordAuthenticationToken.authenticated("operador-teste",null,
            Arrays.stream(permissoes).map(SimpleGrantedAuthority::new).toList());
    }

    @Test void valorNegativoOuFracionadoDemaisRejeitadoPelaApi() throws Exception {
        for (String valor : List.of("-1","0","1.001")) {
            mvc.perform(post("/financeiro/movimentos").with(authentication(usuario("ROLE_OPERADOR"))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"descricao":"Inválido","tipo":"RECEITA","valor":%s,"vencimento":"%s","contaId":"%s","categoriaId":"%s","versao":0}
                    """.formatted(valor,hoje,conta.id(),categoria.id()))).andExpect(status().isBadRequest());
        }
    }


    @Test void provisaoNaoAlteraSaldoEBaixaMoveValorDePrevistoParaPago() {
        var m=movimento(Tipo.DESPESA,"89.90");
        var previsto=financeiro.resumo(hoje,hoje);
        assertThat(previsto.pagarPrevisto()).isEqualByComparingTo("89.90");
        assertThat(previsto.despesas()).isEqualByComparingTo("0");
        assertThat(previsto.resultadoPrevisto()).isEqualByComparingTo("-89.90");
        assertThat(previsto.saldoTotal()).isEqualByComparingTo("100");
        m=financeiro.baixar(m.id(),new BaixaRequest(hoje,m.versao()));
        var pago=financeiro.resumo(hoje,hoje);
        assertThat(pago.pagarPrevisto()).isEqualByComparingTo("0");
        assertThat(pago.despesas()).isEqualByComparingTo("89.90");
        assertThat(pago.saldoTotal()).isEqualByComparingTo("10.10");
        financeiro.estornar(m.id(),new VersaoRequest(m.versao()));
        assertThat(financeiro.resumo(hoje,hoje).pagarPrevisto()).isEqualByComparingTo("89.90");
    }
    @Test void integracaoNaoTemAcessoAoFinanceiroDoOperador() throws Exception {
        mvc.perform(get("/financeiro/contas").with(authentication(usuario("PERM_INTEGRACAO")))).andExpect(status().isForbidden());
        mvc.perform(get("/financeiro/contas").with(authentication(usuario("ROLE_OPERADOR")))).andExpect(status().isOk());
        mvc.perform(get("/financeiro/resumo").param("de",hoje.toString()).param("ate",hoje.toString())
            .with(authentication(usuario("ROLE_OPERADOR")))).andExpect(status().isOk()).andExpect(jsonPath("$.pagarPrevisto").value(0));
    }
    @Test void auditoriaFinanceiraNaoSobreviveAoRollbackDoLancamento() {
        var antes=jdbc.queryForObject("select count(*) from operador_log where detalhe like 'FINANCEIRO_MOVIMENTO:%'",Long.class);
        assertThatThrownBy(() -> new org.springframework.transaction.support.TransactionTemplate(tx).execute(status -> {
            movimento(Tipo.DESPESA,"20"); throw new IllegalStateException("Reverter operação de teste");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select count(*) from operador_log where detalhe like 'FINANCEIRO_MOVIMENTO:%'",Long.class)).isEqualTo(antes);
        assertThat(financeiro.listar(hoje,hoje,null,null,null,null,null,0,30).total()).isZero();
    }
}
