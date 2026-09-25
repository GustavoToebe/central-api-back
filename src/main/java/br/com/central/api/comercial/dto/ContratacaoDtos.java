package br.com.central.api.comercial.dto;

import br.com.central.api.comercial.Periodicidade;
import br.com.central.api.comercial.SituacaoComercial;
import br.com.central.api.comercial.SituacaoProvisionamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ContratacaoDtos {

    private ContratacaoDtos() {
    }

    public record AdicionalContratadoRequest(
            @NotNull(message = "Informe o adicional.") UUID adicionalId,
            @NotNull(message = "Informe a quantidade.")
            @DecimalMin(value = "0.01", message = "A quantidade deve ser positiva.") BigDecimal quantidade
    ) {
    }

    public record CriarContratacaoRequest(
            @NotNull(message = "Informe o cliente.") UUID clienteId,
            @NotNull(message = "Informe o produto.") UUID produtoId,
            @NotNull(message = "Informe o plano.") UUID planoId,
            @NotNull(message = "Informe a periodicidade.") Periodicidade periodicidade,
            @DecimalMin(value = "0", message = "O valor não pode ser negativo.") BigDecimal valor,
            @NotNull(message = "Informe o dia de vencimento.")
            @Min(value = 1, message = "O dia de vencimento fica entre 1 e 28.")
            @Max(value = 28, message = "O dia de vencimento fica entre 1 e 28.") Integer diaVencimento,
            @NotNull(message = "Informe o início.") LocalDate inicio,
            SituacaoComercial situacaoComercial,
            @NotBlank(message = "Informe o nome da instância.") String nomeInstancia,
            @NotBlank(message = "Informe o slug da instância.") String slugInstancia,
            @NotBlank(message = "Informe o nome do administrador.") String adminNome,
            @NotBlank(message = "Informe o e-mail do administrador.")
            @Email(message = "E-mail do administrador inválido.") String adminEmail,
            String observacoes,
            List<@Valid AdicionalContratadoRequest> adicionais
    ) {
    }

    public record AtualizarProvisionamentoRequest(
            @NotBlank(message = "Informe o nome da instância.") String nomeInstancia,
            @NotBlank(message = "Informe o slug da instância.") String slugInstancia,
            @NotBlank(message = "Informe o nome do administrador.") String adminNome,
            @NotBlank(message = "Informe o e-mail do administrador.")
            @Email(message = "E-mail do administrador inválido.") String adminEmail
    ) {
    }

    public record AlterarPlanoRequest(
            @NotNull(message = "Informe o plano.") UUID planoId,
            @NotNull(message = "Informe a periodicidade.") Periodicidade periodicidade,
            @DecimalMin(value = "0", message = "O valor não pode ser negativo.") BigDecimal valor,
            @NotNull(message = "Informe o dia de vencimento.")
            @Min(1) @Max(28) Integer diaVencimento,
            @NotNull(message = "Informe a partir de quando vale o plano novo.") LocalDate aPartirDe,
            String motivo
    ) {
    }

    public record SubstituirAdicionaisRequest(
            List<@Valid AdicionalContratadoRequest> adicionais,
            String motivo
    ) {
    }

    public record MotivoRequest(@NotBlank(message = "Informe o motivo.") String motivo) {
    }

    public record AdicionalContratadoResponse(
            UUID adicionalId,
            String codigo,
            String recursoCodigo,
            BigDecimal quantidadeUnitaria,
            BigDecimal quantidade
    ) {
    }

    public record HistoricoResponse(
            UUID id,
            String acao,
            String motivo,
            UUID operadorId,
            int versaoDireitos,
            Instant criadoEm
    ) {
    }

    public record ContratacaoResumo(
            UUID id,
            UUID clienteId,
            String clienteNome,
            UUID produtoId,
            String produtoCodigo,
            UUID planoId,
            String planoCodigo,
            Periodicidade periodicidade,
            BigDecimal valor,
            int diaVencimento,
            LocalDate vigenteAte,
            SituacaoComercial situacaoComercial,
            SituacaoProvisionamento situacaoProvisionamento,
            int versaoDireitos,
            boolean acessoLiberado,
            String nomeInstancia,
            String slugInstancia
    ) {
    }

    public record ContratacaoResponse(
            UUID id,
            UUID clienteId,
            UUID produtoId,
            String produtoCodigo,
            UUID planoId,
            String planoCodigo,
            String planoNome,
            Periodicidade periodicidade,
            BigDecimal valor,
            int diaVencimento,
            LocalDate inicio,
            LocalDate vigenteAte,
            SituacaoComercial situacaoComercial,
            SituacaoProvisionamento situacaoProvisionamento,
            UUID idempotencyKey,
            UUID idExterno,
            boolean provisionamentoEditavel,
            String nomeInstancia,
            String slugInstancia,
            String adminNome,
            String adminEmail,
            int versaoDireitos,
            DireitosInstancia direitos,
            List<AdicionalContratadoResponse> adicionais,
            List<HistoricoResponse> historico
    ) {
    }
}
