package br.com.central.api.operador.dto;

public record LoginResponse(String accessToken, long expiresInSeconds, OperadorResumo operador) {
}
