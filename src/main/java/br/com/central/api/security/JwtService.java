package br.com.central.api.security;

import br.com.central.api.web.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/** JWT próprio da Central. Não serve no aplicativo, e o JWT do aplicativo não serve aqui. */
@Service
public class JwtService {

    static final String CLAIM_PURPOSE = "purpose";
    static final String PURPOSE_ACCESS = "access";

    private final SecretKey key;
    private final SecurityProperties.Jwt config;

    public JwtService(SecurityProperties properties) {
        this.config = properties.jwt();
        byte[] secretBytes;
        try {
            secretBytes = Base64.getDecoder().decode(config.secret());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("central.security.jwt.secret precisa ser Base64 válido.", e);
        }
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "central.security.jwt.secret precisa decodificar para pelo menos 32 bytes.");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public String gerarAccessToken(UUID operadorId) {
        return gerarAccessToken(operadorId, 0);
    }

    public String gerarAccessToken(UUID operadorId, long versao) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(operadorId.toString())
                .claim(CLAIM_PURPOSE, PURPOSE_ACCESS)
                .claim("cv", versao)
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(config.accessTokenTtl())))
                .signWith(key)
                .compact();
    }

    public long expiresInSeconds() {
        return config.accessTokenTtl().toSeconds();
    }

    public UUID validarAccessToken(String token) {
        return validarAcesso(token).operadorId();
    }

    public Acesso validarAcesso(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (!PURPOSE_ACCESS.equals(claims.get(CLAIM_PURPOSE))) {
                throw new UnauthorizedException("Token recusado.");
            }
            Object cv = claims.get("cv");
            if (cv != null && !(cv instanceof Number)) throw new UnauthorizedException("Token recusado.");
            return new Acesso(UUID.fromString(claims.getSubject()), cv == null ? 0 : ((Number)cv).longValue());
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException("Token recusado.");
        }
    }
    public record Acesso(UUID operadorId, long versao) {}
}
