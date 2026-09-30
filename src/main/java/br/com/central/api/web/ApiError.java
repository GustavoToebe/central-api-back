package br.com.central.api.web;

import java.time.Instant;
import java.util.List;

/**
 * Mesmo corpo de erro do Servirea, com {@code codigo} estável para a máquina
 * (contrato de integração, seção 3).
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String codigo,
        String path,
        String requestId,
        List<FieldError> fieldErrors
) {
    public record FieldError(String field, String message) {
    }
}
