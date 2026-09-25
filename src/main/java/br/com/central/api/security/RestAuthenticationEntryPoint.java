package br.com.central.api.security;

import br.com.central.api.web.ApiError;
import br.com.central.api.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final JsonMapper jsonMapper;

    public RestAuthenticationEntryPoint(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        escrever(response, request, HttpStatus.UNAUTHORIZED, "Autenticação necessária, ou token ausente/inválido/expirado.");
    }

    static void escrever(HttpServletResponse response, HttpServletRequest request, HttpStatus status, String message,
                         JsonMapper jsonMapper) throws IOException {
        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                status.name(),
                message,
                null,
                request.getRequestURI(),
                MDC.get(RequestIdFilter.MDC_KEY),
                null);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getWriter(), body);
    }

    private void escrever(HttpServletResponse response, HttpServletRequest request, HttpStatus status, String message)
            throws IOException {
        escrever(response, request, status, message, jsonMapper);
    }
}
