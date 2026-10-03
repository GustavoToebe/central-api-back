package br.com.central.api.financeiro;

import br.com.central.api.AbstractIntegrationTest;
import br.com.central.api.web.BadRequestException;
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
    /** Conta contábil de entrada (RECEITA) e de saída (DESPESA), cada uma dentro do seu grupo. */
    private CategoriaResponse categoria, categoriaSaida, grupoEntrada, grupoSaida;
    private final LocalDate hoje = LocalDate.of(1905,1,15);
    @BeforeEach void preparar() {
        jdbc.update("delete from financeiro_movimento"); jdbc.update("delete from financeiro_conta"); jdbc.update("delete from financeiro_categoria");
        conta = financeiro.salvarConta(null,new ContaRequest("Caixa", new BigDecimal("100.00"), hoje.minusDays(10),true));
        grupoEntrada = financeiro.salvarCategoria(null,new CategoriaRequest("Receitas",true,Tipo.RECEITA,null));
        grupoSaida = financeiro.salvarCategoria(null,new CategoriaRequest("Despesas",true,Tipo.DESPESA,null));
        categoria = financeiro.salvarCategoria(null,new CategoriaRequest("Doações",true,Tipo.RECEITA,grupoEntrada.id()));
        categoriaSaida = financeiro.salvarCategoria(null,new CategoriaRequest("Infraestrutura",true,Tipo.DESPESA,grupoSaida.id()));
    }
    private MovimentoResponse movimento(Tipo tipo, String valor) {
        return financeiro.salvarMovimento(null,new MovimentoRequest("Teste",tipo,new BigDecimal(valor),hoje,conta.id(),(tipo==Tipo.RECEITA ? categoria : categoriaSaida).id(),null,0L));
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


    @Test void contaBancariaGuardaDadosEChavesPixNormalizadas() {
        var pix=List.of(new ChavePixDto(ChavePixDto.TipoChavePix.CPF,"123.456.789-09",true),new ChavePixDto(ChavePixDto.TipoChavePix.EMAIL," Tesouraria@Paroquia.org ",false));
        var c=financeiro.salvarConta(null,new ContaRequest("Banco principal",BigDecimal.TEN,hoje.minusDays(5),true,br.com.central.api.financeiro.ContaFinanceira.TipoConta.CORRENTE,
            "Banco do Brasil","1234-5","98765-0","Paróquia Teste",hoje.minusYears(1),null,pix));
        var lida=financeiro.contas().stream().filter(x -> x.id().equals(c.id())).findFirst().orElseThrow();
        assertThat(lida.banco()).isEqualTo("Banco do Brasil"); assertThat(lida.titular()).isEqualTo("Paróquia Teste");
        assertThat(lida.chavesPix()).extracting(ChavePixDto::chave).containsExactly("12345678909","tesouraria@paroquia.org");
        assertThat(lida.chavesPix().get(0).principal()).isTrue();
    }
    @Test void contaCorrenteExigeDadosBancariosECaixaNao() {
        assertThatThrownBy(() -> financeiro.salvarConta(null,new ContaRequest("Sem banco",BigDecimal.ZERO,hoje,true,br.com.central.api.financeiro.ContaFinanceira.TipoConta.CORRENTE,null,null,null,null,null,null,null)))
            .isInstanceOf(BadRequestException.class);
        assertThat(financeiro.salvarConta(null,new ContaRequest("Caixa da secretaria",BigDecimal.ZERO,hoje,true,br.com.central.api.financeiro.ContaFinanceira.TipoConta.CAIXA,null,null,null,null,null,null,null)).id()).isNotNull();
    }
    @Test void chavesPixInvalidasRepetidasOuComDuasPrincipaisSaoRecusadas() {
        java.util.function.Function<List<ChavePixDto>,ContaRequest> req = chaves -> new ContaRequest("Pix "+UUID.randomUUID(),BigDecimal.ZERO,hoje,true,br.com.central.api.financeiro.ContaFinanceira.TipoConta.CAIXA,null,null,null,null,null,null,chaves);
        assertThatThrownBy(() -> financeiro.salvarConta(null,req.apply(List.of(new ChavePixDto(ChavePixDto.TipoChavePix.CPF,"123",false))))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> financeiro.salvarConta(null,req.apply(List.of(new ChavePixDto(ChavePixDto.TipoChavePix.EMAIL,"a@b.co",true),new ChavePixDto(ChavePixDto.TipoChavePix.EMAIL,"A@B.co",false))))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> financeiro.salvarConta(null,req.apply(List.of(new ChavePixDto(ChavePixDto.TipoChavePix.EMAIL,"a@b.co",true),new ChavePixDto(ChavePixDto.TipoChavePix.EMAIL,"c@d.co",true))))).isInstanceOf(BadRequestException.class);
    }
    @Test void encerramentoNaoPodeSerAnteriorAAbertura() {
        assertThatThrownBy(() -> financeiro.salvarConta(null,new ContaRequest("Período errado",BigDecimal.ZERO,hoje,true,br.com.central.api.financeiro.ContaFinanceira.TipoConta.CAIXA,null,null,null,null,hoje,hoje.minusDays(1),null)))
            .isInstanceOf(BadRequestException.class);
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
    private CategoriaRequest grupo(String nome, Tipo tipo) { return new CategoriaRequest(nome,true,tipo,null); }
    private CategoriaRequest contaContabil(String nome, Tipo tipo, UUID grupoId) { return new CategoriaRequest(nome,true,tipo,grupoId); }
    private MovimentoRequest lancamento(Tipo tipo, UUID categoriaId) {
        return new MovimentoRequest("Teste",tipo,BigDecimal.TEN,hoje,conta.id(),categoriaId,null,0L);
    }
    @Test void contaContabilPrecisaDeGrupoDoMesmoTipoEGrupoNaoTemGrupo() {
        assertThatThrownBy(() -> financeiro.salvarCategoria(null,contaContabil("Conta errada",Tipo.RECEITA,grupoSaida.id()))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> financeiro.salvarCategoria(null,contaContabil("Dentro de conta",Tipo.RECEITA,categoria.id()))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> financeiro.salvarCategoria(null,contaContabil("Fantasma",Tipo.RECEITA,UUID.randomUUID()))).isInstanceOf(ResourceNotFoundException.class);
        var lista=financeiro.categorias();
        assertThat(lista).filteredOn(CategoriaResponse::ehGrupo).extracting(CategoriaResponse::nome).containsExactlyInAnyOrder("Receitas","Despesas");
        assertThat(lista).filteredOn(c -> !c.ehGrupo()).extracting(CategoriaResponse::nome).containsExactlyInAnyOrder("Doações","Infraestrutura");
    }
    @Test void nomesUnicosPorGrupoENoMesmoTipoDeGrupo() {
        assertThatThrownBy(() -> financeiro.salvarCategoria(null,grupo("receitas",Tipo.RECEITA))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> financeiro.salvarCategoria(null,contaContabil("DOAÇÕES",Tipo.RECEITA,grupoEntrada.id()))).isInstanceOf(ConflictException.class);
        assertThatCode(() -> financeiro.salvarCategoria(null,grupo("Receitas",Tipo.DESPESA))).doesNotThrowAnyException();
        var outroGrupo=financeiro.salvarCategoria(null,grupo("Eventos",Tipo.RECEITA));
        assertThatCode(() -> financeiro.salvarCategoria(null,contaContabil("Doações",Tipo.RECEITA,outroGrupo.id()))).doesNotThrowAnyException();
        assertThatCode(() -> financeiro.salvarCategoria(categoria.id(),contaContabil("Doações",Tipo.RECEITA,grupoEntrada.id()))).doesNotThrowAnyException();
    }
    @Test void conflitoMostraAMensagemParaOOperadorEnaoSoOCodigo() {
        var erro=catchThrowableOfType(ConflictException.class,() -> financeiro.salvarCategoria(null,grupo("Receitas",Tipo.RECEITA)));
        assertThat(erro.getMessage()).contains("grupo com este nome");
    }
    @Test void lancamentoSoEmContaContabilDoMesmoTipoEAtiva() {
        assertThatThrownBy(() -> financeiro.salvarMovimento(null,lancamento(Tipo.RECEITA,grupoEntrada.id()))).isInstanceOf(BadRequestException.class).hasMessageContaining("conta contábil");
        assertThatThrownBy(() -> financeiro.salvarMovimento(null,lancamento(Tipo.DESPESA,categoria.id()))).isInstanceOf(BadRequestException.class).hasMessageContaining("entradas");
        assertThatCode(() -> financeiro.salvarMovimento(null,lancamento(Tipo.DESPESA,categoriaSaida.id()))).doesNotThrowAnyException();
        financeiro.salvarCategoria(grupoSaida.id(),new CategoriaRequest("Despesas",false,Tipo.DESPESA,null));
        assertThatThrownBy(() -> financeiro.salvarMovimento(null,lancamento(Tipo.DESPESA,categoriaSaida.id()))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> financeiro.salvarCategoria(null,contaContabil("Nova",Tipo.DESPESA,grupoSaida.id()))).isInstanceOf(BadRequestException.class);
    }
    @Test void estruturaComUsoNaoMudaDeTipoNemDeNivel() {
        movimento(Tipo.RECEITA,"5");
        assertThatThrownBy(() -> financeiro.salvarCategoria(categoria.id(),contaContabil("Doações",Tipo.DESPESA,grupoSaida.id()))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> financeiro.salvarCategoria(categoria.id(),grupo("Doações",Tipo.RECEITA))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> financeiro.salvarCategoria(grupoEntrada.id(),contaContabil("Receitas",Tipo.RECEITA,grupoEntrada.id()))).isInstanceOf(ConflictException.class);
        var outro=financeiro.salvarCategoria(null,grupo("Outro grupo",Tipo.RECEITA));
        assertThatThrownBy(() -> financeiro.salvarCategoria(grupoEntrada.id(),contaContabil("Receitas",Tipo.RECEITA,outro.id()))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> financeiro.salvarCategoria(grupoEntrada.id(),new CategoriaRequest("Receitas",true,Tipo.DESPESA,null))).isInstanceOf(ConflictException.class);
        assertThatCode(() -> financeiro.salvarCategoria(categoria.id(),contaContabil("Doações",Tipo.RECEITA,outro.id()))).doesNotThrowAnyException();
    }
    @Test void bancoRejeitaAutoReferenciaEGrupoInexistente() {
        assertThatThrownBy(() -> jdbc.update("update financeiro_categoria set grupo_id=id where id=?",categoria.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update financeiro_categoria set grupo_id=? where id=?",UUID.randomUUID(),categoria.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
