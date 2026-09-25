package br.com.central.api.comercial.dto;

import br.com.central.api.comercial.Cobranca;
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

    public record GerarCobrancasRequest(@NotNull(message = "Informe até qual mês gerar.") YearMonth ate) {
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
}
