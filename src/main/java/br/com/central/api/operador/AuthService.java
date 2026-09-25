package br.com.central.api.operador;

import br.com.central.api.security.JwtService;
import br.com.central.api.web.UnauthorizedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    private final OperadorRepository operadorRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final OperadorAuditoria auditoria;

    public AuthService(OperadorRepository operadorRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       OperadorAuditoria auditoria) {
        this.operadorRepository = operadorRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.auditoria = auditoria;
    }

    @Transactional
    public Sessao entrar(String email, String senha, String ip) {
        Operador operador = operadorRepository.findByEmail(email.trim().toLowerCase()).orElse(null);
        if (operador == null || !operador.isAtivo() || !passwordEncoder.matches(senha, operador.getSenhaHash())) {
            auditoria.registrar(operador == null ? null : operador.getId(), "LOGIN_RECUSADO", null, ip);
            throw new UnauthorizedException("E-mail ou senha inválidos.");
        }
        auditoria.registrar(operador.getId(), "LOGIN", null, ip);
        return sessao(operador, refreshTokenService.emitir(operador, ip));
    }

    @Transactional
    public Sessao renovar(String refreshTokenBruto, String ip) {
        RefreshTokenService.Rotacao rotacao = refreshTokenService.rotacionar(refreshTokenBruto, ip);
        return sessao(rotacao.operador(), rotacao.refreshTokenBruto());
    }

    @Transactional
    public void sair(String refreshTokenBruto, String ip) {
        if (refreshTokenBruto == null || refreshTokenBruto.isBlank()) {
            return;
        }
        refreshTokenService.revogar(refreshTokenBruto);
        auditoria.registrar(null, "LOGOUT", null, ip);
    }

    private Sessao sessao(Operador operador, String refresh) {
        return new Sessao(
                jwtService.gerarAccessToken(operador.getId()),
                jwtService.expiresInSeconds(),
                refresh,
                operador.getId(),
                operador.getNome(),
                operador.getEmail());
    }

    public record Sessao(String accessToken, long expiresInSeconds, String refreshTokenBruto,
                         UUID operadorId, String nome, String email) {
    }
}
