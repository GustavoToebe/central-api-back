package br.com.central.api.operador.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String senha,
        @jakarta.validation.constraints.Size(max = 64) String codigoMfa
) {
}
