package br.com.central.api.operador;

import br.com.central.api.operador.dto.LoginRequest;
import br.com.central.api.operador.dto.LoginResponse;
import br.com.central.api.operador.dto.OperadorResumo;
import br.com.central.api.security.SecurityProperties;
import br.com.central.api.web.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    public static final String REFRESH_TOKEN_COOKIE = "central_refresh_token";

    private final AuthService authService;
    private final SecurityProperties properties;
    private final String refreshCookiePath;

    public AuthController(AuthService authService, SecurityProperties properties,
                          @Value("${central.security.refresh-cookie-path:/auth}") String refreshCookiePath) {
        this.authService = authService;
        this.properties = properties;
        this.refreshCookiePath = refreshCookiePath;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody @Valid LoginRequest request,
                               HttpServletRequest httpRequest,
                               HttpServletResponse httpResponse) {
        AuthService.Sessao sessao = authService.entrar(request.email(), request.senha(), ip(httpRequest));
        gravarCookie(httpResponse, sessao.refreshTokenBruto());
        return resposta(sessao);
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshTokenBruto,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        if (refreshTokenBruto == null || refreshTokenBruto.isBlank()) {
            throw new UnauthorizedException("Sessão inválida. Entre de novo.");
        }
        AuthService.Sessao sessao = authService.renovar(refreshTokenBruto, ip(httpRequest));
        gravarCookie(httpResponse, sessao.refreshTokenBruto());
        return resposta(sessao);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshTokenBruto,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        authService.sair(refreshTokenBruto, ip(httpRequest));
        limparCookie(httpResponse);
        return ResponseEntity.noContent().build();
    }

    private LoginResponse resposta(AuthService.Sessao sessao) {
        return new LoginResponse(
                sessao.accessToken(),
                sessao.expiresInSeconds(),
                new OperadorResumo(sessao.operadorId(), sessao.nome(), sessao.email()));
    }

    private void gravarCookie(HttpServletResponse response, String refreshTokenBruto) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(refreshTokenBruto, properties.refreshTokenTtl().toSeconds()).toString());
    }

    private void limparCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", 0).toString());
    }

    private ResponseCookie cookie(String valor, long maxAgeSeconds) {
        return cookieDeRefresh(refreshCookiePath, valor, maxAgeSeconds);
    }

    /** Também usado pela troca de senha, que devolve um refresh novo fora de /auth. */
    static ResponseCookie cookieDeRefresh(String caminho, String valor, long maxAgeSeconds) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, valor)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path(caminho)
                .maxAge(maxAgeSeconds)
                .build();
    }

    private String ip(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
