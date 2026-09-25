package br.com.central.api.integracao;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sonda da fundação. A listagem de direitos mora em
 * {@link IntegracaoDireitosController}.
 */
@RestController
@RequestMapping("/integracao/v1")
public class IntegracaoSaudeController {

    @GetMapping("/saude")
    @PreAuthorize("hasAuthority('PERM_INTEGRACAO')")
    public ResponseEntity<Void> saude() {
        return ResponseEntity.noContent().build();
    }
}
