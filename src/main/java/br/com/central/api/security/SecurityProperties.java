package br.com.central.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "central.security")
public record SecurityProperties(Jwt jwt, Duration refreshTokenTtl, Cors cors) {

    public record Jwt(String secret, Duration accessTokenTtl) {
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
