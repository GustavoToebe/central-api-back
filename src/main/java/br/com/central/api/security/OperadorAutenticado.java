package br.com.central.api.security;

import java.util.UUID;

public record OperadorAutenticado(UUID id, String email) {
}
