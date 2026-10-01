package br.com.central.api.operador;

import br.com.central.api.security.JwtService;
import br.com.central.api.web.BadRequestException;
import br.com.central.api.web.ResourceNotFoundException;
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
    private final MfaService mfa;

    public AuthService(OperadorRepository operadorRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       OperadorAuditoria auditoria, MfaService mfa) {
        this.operadorRepository = operadorRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.auditoria = auditoria;
        this.mfa = mfa;
    }

    @Transactional
    public Sessao entrar(String email, String senha, String ip) {
        return entrar(email, senha, ip, null);
    }

    @Transactional
    public Sessao entrar(String email, String senha, String ip, String codigoMfa) {
        Operador operador = operadorRepository.buscarParaAutenticar(email.trim().toLowerCase(java.util.Locale.ROOT)).orElse(null);
        if (operador == null || !operador.isAtivo() || !passwordEncoder.matches(senha, operador.getSenhaHash())) {
            auditoria.registrar(operador == null ? null : operador.getId(), "LOGIN_RECUSADO", null, ip);
            throw new UnauthorizedException("E-mail ou senha inválidos.");
        }
        try {mfa.verificar(operador, codigoMfa);}
        catch (MfaException ex) {
            if (!"MFA_NECESSARIO".equals(ex.getCodigo())) auditoria.registrar(operador.getId(), "LOGIN_MFA_RECUSADO", null, ip);
            throw ex;
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

    /**
     * Troca a própria senha (27/09/2026: a senha do primeiro operador passou pelo chat). Derruba as outras
     * sessões e devolve um refresh token novo para esta continuar aberta.
     */
    @Transactional
    public String trocarSenha(UUID operadorId, String senhaAtual, String novaSenha, String ip) {
        Operador operador = operadorRepository.buscarParaAlterar(operadorId)
                .orElseThrow(() -> new ResourceNotFoundException("Operador não encontrado."));
        if (!passwordEncoder.matches(senhaAtual, operador.getSenhaHash())) {
            auditoria.registrar(operadorId, "SENHA_RECUSADA", null, ip);
            throw new BadRequestException("A senha atual não confere.");
        }
        if (passwordEncoder.matches(novaSenha, operador.getSenhaHash())) {
            throw new BadRequestException("A nova senha precisa ser diferente da atual.");
        }
        operador.trocarSenha(passwordEncoder.encode(novaSenha));
        operadorRepository.save(operador);
        refreshTokenService.encerrarTodas(operadorId);
        auditoria.registrar(operadorId, "SENHA_ALTERADA", null, ip);
        return refreshTokenService.emitir(operador, ip);
    }

    private Sessao sessao(Operador operador, String refresh) {
        return new Sessao(
                jwtService.gerarAccessToken(operador.getId(), operador.getCredenciaisVersao()),
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
