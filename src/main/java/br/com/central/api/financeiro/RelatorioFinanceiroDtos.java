package br.com.central.api.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import static br.com.central.api.financeiro.MovimentoFinanceiro.Tipo;

/** Respostas dos relatórios financeiros do operador. Valores em reais; datas já no calendário de São Paulo. */
public final class RelatorioFinanceiroDtos {
    private RelatorioFinanceiroDtos() { }

    /** REALIZADO = baixados no período (pela data da baixa); PREVISTO = pendentes com vencimento no período. */
    public enum Visao { REALIZADO, PREVISTO }

    public record Lancamento(UUID id, LocalDate data, String descricao, String contaBanco, BigDecimal valor) { }
    public record ContaContabilLinha(UUID id, String nome, BigDecimal total, List<Lancamento> lancamentos) { }
    /** `comercial` = grupo calculado das cobranças de assinatura (não é um grupo do plano de contas). */
    public record GrupoLinha(UUID id, String nome, boolean comercial, BigDecimal total, List<ContaContabilLinha> contas) { }
    public record PorTipo(LocalDate de, LocalDate ate, Visao visao, Tipo tipo, BigDecimal total, List<GrupoLinha> grupos) { }

    public record MovimentoBanco(UUID id, LocalDate data, String descricao, String contaContabil, BigDecimal entrada, BigDecimal saida, BigDecimal saldo) { }
    public record ContaBanco(UUID id, String nome, boolean ativo, BigDecimal saldoAnterior, BigDecimal entradas, BigDecimal saidas,
                             BigDecimal saldoFinal, List<MovimentoBanco> movimentos) { }
    public record BancoCaixa(LocalDate de, LocalDate ate, BigDecimal saldoAnterior, BigDecimal entradas, BigDecimal saidas, BigDecimal saldoFinal,
                             String observacao, List<ContaBanco> contas) { }

    public record Demonstrativo(LocalDate de, LocalDate ate, PorTipo receitas, PorTipo despesas, BigDecimal totalReceitas, BigDecimal totalDespesas,
                                BigDecimal resultado, BigDecimal aReceber, BigDecimal aPagar, BigDecimal resultadoPrevisto,
                                List<FinanceiroDtos.SaldoConta> saldos) { }
}
