package br.com.central.api.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        log.warn("Erro de negócio tratado: status={} codigo={} message={}", ex.getStatus(), ex.getCodigo(), ex.getMessage());
        return build(ex.getStatus(), ex.getMessage(), ex.getCodigo(), request, null);
    }

    /**
     * Relança para o {@code ExceptionTranslationFilter}. Sem isto o catch-all
     * abaixo transforma o 403 do {@code @PreAuthorize} em 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDenied(AccessDeniedException ex) {
        throw ex;
    }

    @ExceptionHandler(AuthenticationException.class)
    public void handleAuthentication(AuthenticationException ex) {
        throw ex;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<ApiError.FieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldError(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Requisição inválida.", "DADOS_INVALIDOS", request, fieldErrors);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Erro não tratado ao processar requisição", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocorreu um erro inesperado. Tente novamente ou contate o suporte informando o requestId.",
                null, request, null);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        HttpServletRequest httpRequest = ((ServletWebRequest) request).getRequest();
        ApiError body = corpo(HttpStatus.BAD_REQUEST, "Um ou mais campos são inválidos.", "DADOS_INVALIDOS", httpRequest, fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        HttpServletRequest httpRequest = ((ServletWebRequest) request).getRequest();
        ApiError body = corpo(HttpStatus.BAD_REQUEST, "Corpo da requisição malformado ou ilegível.", "DADOS_INVALIDOS", httpRequest, null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, String codigo, HttpServletRequest request,
                                           List<ApiError.FieldError> fieldErrors) {
        return ResponseEntity.status(status).body(corpo(status, message, codigo, request, fieldErrors));
    }

    private ApiError corpo(HttpStatus status, String message, String codigo, HttpServletRequest request,
                           List<ApiError.FieldError> fieldErrors) {
        return new ApiError(
                Instant.now(),
                status.value(),
                status.name(),
                message,
                codigo,
                request != null ? request.getRequestURI() : null,
                MDC.get(RequestIdFilter.MDC_KEY),
                fieldErrors
        );
    }
}
