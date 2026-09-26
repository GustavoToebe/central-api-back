package br.com.central.api.comercial.dto;

import br.com.central.api.comercial.Cobranca;
import br.com.central.api.comercial.CobrancaItem;
import br.com.central.api.comercial.Contratacao;
import br.com.central.api.comercial.FormaPagamento;
import br.com.central.api.comercial.Periodicidade;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public final class FinanceiroDtos {

    private FinanceiroDtos() {
    }

    public record RegistrarPagamentoRequest(
            @NotEmpty(message = "Informe ao menos uma cobrança.") List<UUID> cobrancaIds,
            @NotNull(message = "Informe a data do pagamento.") LocalDate pagoEm,
            @NotNull(message = "Informe a forma de pagamento.") FormaPagamento formaPagamento,
            BigDecimal valorPago,
            String observacao
    ) {
    }

    public record IsentarCobrancaRequest(String motivo) {
    }

    /** {@code de} vazio = desde o início da contratação. Só cria as que faltam. */
    public record GerarCobrancasRequest(
            YearMonth de,
            @NotNull(message = "Informe até qual competência gerar.") YearMonth ate
    ) {
    }

    public record CobrancaResponse(
            UUID id,
            UUID contratacaoId,
            LocalDate competenciaInicio,
            LocalDate competenciaFim,
            LocalDate vencimento,
            BigDecimal valor,
            Cobranca.Status status,
            boolean vencida,
            LocalDate pagoEm,
            BigDecimal valorPago,
            FormaPagamento formaPagamento,
            String observacao
    ) {
        public static CobrancaResponse de(Cobranca cobranca, LocalDate hoje) {
            return new CobrancaResponse(
                    cobranca.getId(), cobranca.getContratacaoId(), cobranca.getCompetenciaInicio(),
                    cobranca.getCompetenciaFim(), cobranca.getVencimento(), cobranca.getValor(),
                    cobranca.getStatus(), cobranca.vencidaEm(hoje), cobranca.getPagoEm(),
                    cobranca.getValorPago(), cobranca.getFormaPagamento(), cobranca.getObservacao());
        }
    }

    public record Resumo(int vencidas, long diasAtraso, BigDecimal valorEmAtraso,
                         LocalDate proximoVencimento, BigDecimal totalPagoNoAno) {
    }

    public record FinanceiroResponse(List<CobrancaResponse> cobrancas, Resumo resumo) {
    }

    public record SituacaoFinanceira(
            String planoNome,
            Periodicidade periodicidade,
            long cobrancasVencidas,
            long diasAtraso
    ) {
    }

    /** Filtros da lista geral; todos opcionais. {@code situacao} aceita também VENCIDA. */
    public record FiltroCobrancas(
            UUID produtoId,
            String situacao,
            FormaPagamento formaPagamento,
            LocalDate vencimentoDe,
            LocalDate vencimentoAte,
            YearMonth competencia,
            String busca
    ) {
    }

    /** Linha da tela "Cobranças": a cobrança com o cliente e a contratação. */
    public record CobrancaLinha(
            UUID id,
            UUID contratacaoId,
            UUID clienteId,
            String clienteNome,
            String produtoCodigo,
            String nomeInstancia,
            String planoNome,
            Periodicidade periodicidade,
            LocalDate competenciaInicio,
            LocalDate competenciaFim,
            LocalDate vencimento,
            BigDecimal valor,
            Cobranca.Status status,
            boolean vencida,
            LocalDate pagoEm,
            BigDecimal valorPago,
            FormaPagamento formaPagamento
    ) {
        public static CobrancaLinha de(Cobranca cobranca, LocalDate hoje) {
            Contratacao contratacao = cobranca.getContratacao();
            return new CobrancaLinha(
                    cobranca.getId(), cobranca.getContratacaoId(), contratacao.getCliente().getId(),
                    contratacao.getCliente().getNome(), contratacao.getProduto().getCodigo(),
                    contratacao.getNomeInstancia(), contratacao.getPlano().getNome(), contratacao.getPeriodicidade(),
                    cobranca.getCompetenciaInicio(), cobranca.getCompetenciaFim(), cobranca.getVencimento(),
                    cobranca.getValor(), cobranca.getStatus(), cobranca.vencidaEm(hoje), cobranca.getPagoEm(),
                    cobranca.getValorPago(), cobranca.getFormaPagamento());
        }
    }

    public record CobrancaItemResponse(
            CobrancaItem.Tipo tipo,
            String descricao,
            BigDecimal quantidade,
            BigDecimal valorUnitario,
            int meses,
            BigDecimal valor
    ) {
        public static CobrancaItemResponse de(CobrancaItem item) {
            return new CobrancaItemResponse(item.getTipo(), item.getDescricao(), item.getQuantidade(),
                    item.getValorUnitario(), item.getMeses(), item.getValor());
        }
    }

    public record CobrancaDetalhe(
            CobrancaLinha cobranca,
            String observacao,
            List<CobrancaItemResponse> itens
    ) {
    }
}
