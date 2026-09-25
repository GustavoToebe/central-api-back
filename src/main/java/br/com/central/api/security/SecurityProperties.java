package br.com.central.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "central.security")
public record SecurityProperties(Jwt jwt, Duration refreshTokenTtl, Cors cors, Csrf csrf) {

    public record Jwt(String secret, Duration accessTokenTtl) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    /**
     * Domínio do cookie {@code XSRF-TOKEN}. Vazio = só o host da API. Com o
     * painel e a API em subdomínios diferentes (ex.: {@code central.servirea.com.br}
     * e {@code api-central.servirea.com.br}), o front só lê o cookie se ele for
     * do domínio pai ({@code servirea.com.br}); sem isso refresh e logout dão 403.
     */
    public record Csrf(String cookieDomain) {
    }
}
