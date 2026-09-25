package br.com.central.api.web;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        this(message, null);
    }

    public ResourceNotFoundException(String message, String codigo) {
        super(HttpStatus.NOT_FOUND, message, codigo);
    }
}
