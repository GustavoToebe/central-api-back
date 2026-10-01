package br.com.central.api.operador;

import br.com.central.api.security.OperadorAutenticado;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/operadores/eu/mfa")
public class MfaController {
    private final MfaService mfa;
    private final LimiteLogin limite;
    public MfaController(MfaService mfa, LimiteLogin limite) {this.mfa = mfa; this.limite = limite;}
    @GetMapping
    @PreAuthorize("hasRole('OPERADOR')")
    public MfaService.Status status(@AuthenticationPrincipal OperadorAutenticado op, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); return mfa.status(op.id());
    }
    @PostMapping("/preparar")
    @PreAuthorize("hasRole('OPERADOR')")
    public MfaService.Preparacao preparar(@AuthenticationPrincipal OperadorAutenticado op, @Valid @RequestBody Senha body,
                                         HttpServletRequest req, HttpServletResponse res) {
        limitar(op, req, res); return mfa.preparar(op.id(), body.senha(), req.getRemoteAddr());
    }
    @PostMapping("/ativar")
    @PreAuthorize("hasRole('OPERADOR')")
    public MfaService.Recuperacao ativar(@AuthenticationPrincipal OperadorAutenticado op, @Valid @RequestBody Confirmacao body,
                                        HttpServletRequest req, HttpServletResponse res) {
        limitar(op, req, res); return mfa.ativar(op.id(), body.senha(), body.codigo(), req.getRemoteAddr());
    }
    @PostMapping("/desativar")
    @PreAuthorize("hasRole('OPERADOR')")
    public ResponseEntity<Void> desativar(@AuthenticationPrincipal OperadorAutenticado op, @Valid @RequestBody Confirmacao body,
                                         HttpServletRequest req, HttpServletResponse res) {
        limitar(op, req, res); mfa.desativar(op.id(), body.senha(), body.codigo(), req.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
    private void limitar(OperadorAutenticado op, HttpServletRequest req, HttpServletResponse res) {
        res.setHeader("Cache-Control", "no-store");
        try {limite.registrar(req.getRemoteAddr(), op.email());}
        catch (LimiteLoginException ex) {res.setHeader("Retry-After", Long.toString(ex.segundos())); throw ex;}
    }
    public record Senha(@NotBlank @Size(max = 72) String senha) {}
    public record Confirmacao(@NotBlank @Size(max = 72) String senha, @NotBlank @Size(max = 64) String codigo) {}
}
