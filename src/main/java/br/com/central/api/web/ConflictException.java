package br.com.central.api.web;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {

    public ConflictException(String message, String codigo) {
        super(HttpStatus.CONFLICT, message, codigo);
    }
}
