package br.com.central.api.integracao;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sonda da fundação. Os endpoints do contrato entram no passo da integração;
 * esta rota existe para o filtro ter um controller que exige {@code PERM_INTEGRACAO}.
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
