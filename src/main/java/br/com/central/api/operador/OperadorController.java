package br.com.central.api.operador;

import br.com.central.api.operador.dto.OperadorResumo;
import br.com.central.api.operador.dto.TrocaSenhaRequest;
import br.com.central.api.security.OperadorAutenticado;
import br.com.central.api.security.SecurityProperties;
import br.com.central.api.web.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/operadores")
public class OperadorController {

    private final OperadorRepository operadorRepository;
    private final AuthService authService;
    private final SecurityProperties properties;

    public OperadorController(OperadorRepository operadorRepository, AuthService authService,
                              SecurityProperties properties) {
        this.operadorRepository = operadorRepository;
        this.authService = authService;
        this.properties = properties;
    }

    @GetMapping("/eu")
    @PreAuthorize("hasAuthority('ROLE_OPERADOR')")
    public OperadorResumo eu(@AuthenticationPrincipal OperadorAutenticado atual) {
        Operador operador = operadorRepository.findById(atual.id())
                .orElseThrow(() -> new ResourceNotFoundException("Operador não encontrado."));
        return new OperadorResumo(operador.getId(), operador.getNome(), operador.getEmail());
    }

    /**
     * Troca a própria senha. Fica fora de {@code /auth} porque exige o Bearer; o cookie de refresh novo
     * (as outras sessões caem) volta com {@code path=/auth}, como no login.
     */
    @PutMapping("/eu/senha")
    @PreAuthorize("hasAuthority('ROLE_OPERADOR')")
    public ResponseEntity<Void> trocarSenha(@AuthenticationPrincipal OperadorAutenticado atual,
                                            @RequestBody @Valid TrocaSenhaRequest request,
                                            HttpServletRequest httpRequest) {
        String refresh = authService.trocarSenha(atual.id(), request.senhaAtual(), request.novaSenha(),
                httpRequest.getRemoteAddr());
        String cookie = AuthController.cookieDeRefresh(refresh, properties.refreshTokenTtl().toSeconds()).toString();
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie).build();
    }
}
