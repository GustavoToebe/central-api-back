package br.com.central.api.operador;

import br.com.central.api.security.OpaqueTokenGenerator;
import br.com.central.api.security.SecurityProperties;
import br.com.central.api.web.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final SecurityProperties properties;
    private final RevogacaoDeSessao revogacaoDeSessao;

    public RefreshTokenService(RefreshTokenRepository repository,
                               SecurityProperties properties,
                               RevogacaoDeSessao revogacaoDeSessao) {
        this.repository = repository;
        this.properties = properties;
        this.revogacaoDeSessao = revogacaoDeSessao;
    }

    @Transactional
    public String emitir(Operador operador, String ip) {
        String bruto = OpaqueTokenGenerator.gerar();
        repository.save(new RefreshToken(
                operador, OpaqueTokenGenerator.hash(bruto),
                Instant.now().plus(properties.refreshTokenTtl()), ip));
        return bruto;
    }

    @Transactional
    public Rotacao rotacionar(String tokenBruto, String ip) {
        RefreshToken atual = repository.findByTokenHash(OpaqueTokenGenerator.hash(tokenBruto))
                .orElseThrow(() -> new UnauthorizedException("Sessão inválida. Entre de novo."));
        if (atual.isRevogado()) {
            revogacaoDeSessao.revogarTodos(atual.getOperador().getId());
            throw new UnauthorizedException("Sessão inválida. Entre de novo.");
        }
        if (atual.isExpirado(Instant.now())) {
            throw new UnauthorizedException("Sessão expirada. Entre de novo.");
        }
        Operador operador = atual.getOperador();
        String novo = emitir(operador, ip);
        atual.revogar(Instant.now());
        repository.save(atual);
        return new Rotacao(operador, novo);
    }

    @Transactional
    public void revogar(String tokenBruto) {
        repository.findByTokenHash(OpaqueTokenGenerator.hash(tokenBruto)).ifPresent(token -> {
            if (!token.isRevogado()) {
                token.revogar(Instant.now());
                repository.save(token);
            }
        });
    }

    /**
     * Troca de senha: encerra todas as sessões do operador, na mesma transação. Apaga os tokens em vez de
     * revogar: um token revogado apresentado de novo conta como reuso e derrubaria também a sessão nova.
     */
    @Transactional
    public void encerrarTodas(java.util.UUID operadorId) {
        repository.apagarTodos(operadorId);
    }

    public record Rotacao(Operador operador, String refreshTokenBruto) {
    }
}
