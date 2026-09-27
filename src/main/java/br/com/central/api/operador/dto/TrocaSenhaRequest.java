package br.com.central.api.operador.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Troca da própria senha em "Meu perfil". 72 é o limite do BCrypt. */
public record TrocaSenhaRequest(
        @NotBlank String senhaAtual,
        @NotBlank @Size(min = 8, max = 72, message = "A nova senha precisa ter entre 8 e 72 caracteres.") String novaSenha
) {
}
