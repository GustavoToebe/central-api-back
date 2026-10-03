package br.com.central.api.financeiro;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import static br.com.central.api.financeiro.MovimentoFinanceiro.*;

public final class FinanceiroDtos {
    private FinanceiroDtos() { }
    /** Conta ou caixa. Em conta corrente/poupança, banco, agência, conta e titular são obrigatórios; `tipoConta` ausente vale OUTRA. */
    public record ContaRequest(@NotBlank @Size(max=120) String nome, @NotNull @Digits(integer=12,fraction=2) BigDecimal saldoInicial,
                               @NotNull LocalDate dataSaldoInicial, boolean ativo, ContaFinanceira.TipoConta tipoConta,
                               @Size(max=120) String banco, @Size(max=20) String agencia, @Size(max=30) String numeroConta, @Size(max=120) String titular,
                               LocalDate dataAbertura, LocalDate dataEncerramento, @Size(max=10) List<@Valid ChavePixDto> chavesPix) {
        public ContaRequest(String nome, BigDecimal saldoInicial, LocalDate dataSaldoInicial, boolean ativo) {
            this(nome, saldoInicial, dataSaldoInicial, ativo, null, null, null, null, null, null, null, null);
        }
    }
    /** Grupo (sem grupoId) ou conta contábil (com grupoId). O tipo da conta contábil precisa ser o do grupo. */
    public record CategoriaRequest(@NotBlank @Size(max=120) String nome, boolean ativo, @NotNull Tipo tipo, UUID grupoId) { }
    public record MovimentoRequest(@NotBlank @Size(max=200) String descricao, @NotNull Tipo tipo,
          @NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal valor,
          @NotNull LocalDate vencimento, @NotNull UUID contaId, @NotNull UUID categoriaId,
          @Size(max=1000) String observacoes, @NotNull @Min(0) Long versao) { }
    public record BaixaRequest(@NotNull LocalDate dataPagamento, @NotNull @Min(0) Long versao) { }
    public record VersaoRequest(@NotNull @Min(0) Long versao) { }
    public record ContaResponse(UUID id, String nome, BigDecimal saldoInicial, LocalDate dataSaldoInicial, boolean ativo, ContaFinanceira.TipoConta tipoConta,
                                String banco, String agencia, String numeroConta, String titular, LocalDate dataAbertura, LocalDate dataEncerramento, List<ChavePixDto> chavesPix) {
        public static ContaResponse de(ContaFinanceira c) {
            return new ContaResponse(c.getId(),c.getNome(),c.getSaldoInicial(),c.getDataSaldoInicial(),c.isAtivo(),c.getTipoConta(),
                c.getBanco(),c.getAgencia(),c.getNumeroConta(),c.getTitular(),c.getDataAbertura(),c.getDataEncerramento(),c.getChavesPix());
        }
    }
    public record CategoriaResponse(UUID id, String nome, boolean ativo, Tipo tipo, UUID grupoId, boolean ehGrupo) {
        public static CategoriaResponse de(CategoriaFinanceira c) { return new CategoriaResponse(c.getId(),c.getNome(),c.isAtivo(),c.getTipo(),c.getGrupoId(),c.isGrupo()); }
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
