package br.com.central.api.operador;

import br.com.central.api.operador.dto.OperadorResumo;
import br.com.central.api.security.OperadorAutenticado;
import br.com.central.api.web.ResourceNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/operadores")
public class OperadorController {

    private final OperadorRepository operadorRepository;

    public OperadorController(OperadorRepository operadorRepository) {
        this.operadorRepository = operadorRepository;
    }

    @GetMapping("/eu")
    @PreAuthorize("hasAuthority('ROLE_OPERADOR')")
    public OperadorResumo eu(@AuthenticationPrincipal OperadorAutenticado atual) {
        Operador operador = operadorRepository.findById(atual.id())
                .orElseThrow(() -> new ResourceNotFoundException("Operador não encontrado."));
        return new OperadorResumo(operador.getId(), operador.getNome(), operador.getEmail());
    }
}
