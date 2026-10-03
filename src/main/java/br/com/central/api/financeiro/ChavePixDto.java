package br.com.central.api.financeiro;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Chave PIX de uma conta. Guardada junto da conta (lista JSON); no máximo uma é a principal. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChavePixDto(@NotNull TipoChavePix tipo, @NotBlank @Size(max = 140) String chave, boolean principal) {
    public enum TipoChavePix { CPF, CNPJ, EMAIL, TELEFONE, ALEATORIA }
}
