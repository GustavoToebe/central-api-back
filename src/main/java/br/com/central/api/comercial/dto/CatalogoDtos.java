package br.com.central.api.comercial.dto;

import br.com.central.api.comercial.Periodicidade;
import br.com.central.api.comercial.TipoRecurso;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CatalogoDtos {

    private CatalogoDtos() {
    }

    public record SalvarProdutoRequest(
            @NotBlank(message = "Informe o código do produto.") String codigo,
            @NotBlank(message = "Informe o nome do produto.") String nome,
            String urlBaseIntegracao,
            Boolean ativo
    ) {
    }

    public record ProdutoResponse(UUID id, String codigo, String nome, String urlBaseIntegracao, boolean ativo) {
    }

    public record SalvarRecursoRequest(
            @NotNull(message = "Informe o produto.") UUID produtoId,
            @NotBlank(message = "Informe o código do recurso.") String codigo,
            @NotBlank(message = "Informe o nome do recurso.") String nome,
            @NotNull(message = "Informe o tipo do recurso.") TipoRecurso tipo,
            String unidade,
            @DecimalMin(value = "0", message = "O valor padrão não pode ser negativo.") BigDecimal valorPadrao
    ) {
        public SalvarRecursoRequest(UUID produtoId, String codigo, String nome, TipoRecurso tipo, String unidade) {
            this(produtoId, codigo, nome, tipo, unidade, null);
        }
    }

    public record RecursoResponse(UUID id, UUID produtoId, String codigo, String nome, TipoRecurso tipo, String unidade,
                                  BigDecimal valorPadrao) {
    }

    public record RecursoDoPlanoRequest(
            @NotNull(message = "Informe o recurso.") UUID recursoId,
            @NotNull(message = "Informe o valor do recurso no plano.")
            @DecimalMin(value = "0", message = "O valor do recurso não pode ser negativo.") BigDecimal valor
    ) {
    }

    public record SalvarPlanoRequest(
            @NotNull(message = "Informe o produto.") UUID produtoId,
            @NotBlank(message = "Informe o código do plano.") String codigo,
            @NotBlank(message = "Informe o nome do plano.") String nome,
            Boolean ativo,
            List<@Valid RecursoDoPlanoRequest> recursos,
            List<@Valid PrecoDoPlanoRequest> precos
    ) {
        public SalvarPlanoRequest(UUID produtoId, String codigo, String nome, Boolean ativo,
                                  List<RecursoDoPlanoRequest> recursos) {
            this(produtoId, codigo, nome, ativo, recursos, null);
        }
    }

    /**
     * Preço de uma periodicidade informado no próprio cadastro do plano
     * (26/09/2026). Vale a partir de hoje; periodicidade fora da lista não muda.
     */
    public record PrecoDoPlanoRequest(
            @NotNull(message = "Informe a periodicidade.") Periodicidade periodicidade,
            @NotNull(message = "Informe o valor.")
            @DecimalMin(value = "0", message = "O preço não pode ser negativo.") BigDecimal valor
    ) {
    }

    public record PrecoVigenteResponse(Periodicidade periodicidade, BigDecimal valor) {
    }

    public record RecursoDoPlanoResponse(UUID recursoId, String codigo, TipoRecurso tipo, BigDecimal valor) {
    }

    public record PrecoResponse(UUID id, Periodicidade periodicidade, BigDecimal valor, LocalDate vigenteDesde) {
    }

    public record PlanoResponse(
            UUID id,
            UUID produtoId,
            String codigo,
            String nome,
            boolean ativo,
            /** Preço em vigor hoje, na ordem mensal → anual; periodicidade sem preço não aparece. */
            List<PrecoVigenteResponse> precosVigentes,
            List<RecursoDoPlanoResponse> recursos,
            List<PrecoResponse> precos
    ) {
    }

    public record NovoPrecoRequest(
            @NotNull(message = "Informe a periodicidade.") Periodicidade periodicidade,
            @NotNull(message = "Informe o valor.")
            @DecimalMin(value = "0", message = "O preço não pode ser negativo.") BigDecimal valor,
            @NotNull(message = "Informe a data de vigência.") LocalDate vigenteDesde
    ) {
    }

    public record SalvarAdicionalRequest(
            @NotNull(message = "Informe o produto.") UUID produtoId,
            @NotNull(message = "Informe o recurso.") UUID recursoId,
            @NotBlank(message = "Informe o código do adicional.") String codigo,
            @NotBlank(message = "Informe o nome do adicional.") String nome,
            @NotNull(message = "Informe a quantidade.")
            @DecimalMin(value = "0.01", message = "A quantidade do adicional deve ser positiva.") BigDecimal quantidade,
            @NotNull(message = "Informe o preço.")
            @DecimalMin(value = "0", message = "O preço não pode ser negativo.") BigDecimal preco,
            Boolean ativo
    ) {
    }

    public record AdicionalResponse(
            UUID id,
            UUID produtoId,
            UUID recursoId,
            String recursoCodigo,
            String codigo,
            String nome,
            BigDecimal quantidade,
            BigDecimal preco,
            boolean ativo
    ) {
    }
}
