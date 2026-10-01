package br.com.central.api.financeiro;
import jakarta.validation.constraints.*;
import java.util.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import static br.com.central.api.financeiro.MovimentoFinanceiro.*;

public final class FinanceiroDtos {
    private FinanceiroDtos() { }
    public record ContaRequest(@NotBlank @Size(max=120) String nome, @NotNull @Digits(integer=12,fraction=2) BigDecimal saldoInicial,
                               @NotNull LocalDate dataSaldoInicial, boolean ativo) { }
    public record CategoriaRequest(@NotBlank @Size(max=120) String nome, boolean ativo) { }
    public record MovimentoRequest(@NotBlank @Size(max=200) String descricao, @NotNull Tipo tipo,
          @NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal valor,
          @NotNull LocalDate vencimento, @NotNull UUID contaId, @NotNull UUID categoriaId,
          @Size(max=1000) String observacoes, @NotNull @Min(0) Long versao) { }
    public record BaixaRequest(@NotNull LocalDate dataPagamento, @NotNull @Min(0) Long versao) { }
    public record VersaoRequest(@NotNull @Min(0) Long versao) { }
    public record ContaResponse(UUID id, String nome, BigDecimal saldoInicial, LocalDate dataSaldoInicial, boolean ativo) {
        public static ContaResponse de(ContaFinanceira c) { return new ContaResponse(c.getId(),c.getNome(),c.getSaldoInicial(),c.getDataSaldoInicial(),c.isAtivo()); }
    }
    public record CategoriaResponse(UUID id, String nome, boolean ativo) {
        public static CategoriaResponse de(CategoriaFinanceira c) { return new CategoriaResponse(c.getId(),c.getNome(),c.isAtivo()); }
    }
    public record MovimentoResponse(UUID id, long versao, String descricao, Tipo tipo, Situacao situacao,
          BigDecimal valor, LocalDate vencimento, LocalDate dataPagamento, UUID contaId, String conta,
          UUID categoriaId, String categoria, String observacoes) {
        public static MovimentoResponse de(MovimentoFinanceiro m) {
            return new MovimentoResponse(m.getId(),m.getVersao(),m.getDescricao(),m.getTipo(),m.getSituacao(),m.getValor(),m.getVencimento(),
                m.getDataPagamento(),m.getConta().getId(),m.getConta().getNome(),m.getCategoria().getId(),m.getCategoria().getNome(),m.getObservacoes());
        }
    }
    public record Pagina(List<MovimentoResponse> itens, long total, int pagina, int tamanho) { }
    public record SaldoConta(UUID id, String nome, BigDecimal saldo) { }
    public record Resumo(LocalDate de, LocalDate ate, BigDecimal receitas, BigDecimal despesas, BigDecimal resultado,
                         BigDecimal saldoTotal, List<SaldoConta> contas, BigDecimal receitasCobrancas, BigDecimal receitasManuais,
                         BigDecimal receberPrevisto, BigDecimal pagarPrevisto, BigDecimal resultadoPrevisto) { }
}
