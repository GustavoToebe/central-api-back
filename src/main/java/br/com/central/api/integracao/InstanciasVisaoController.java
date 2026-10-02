package br.com.central.api.integracao;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Painel conjunto de instâncias (F06/F26): leitura dos resumos guardados e atualização manual limitada. */
@RestController
public class InstanciasVisaoController {
    private final InstanciasVisaoService service;

    public InstanciasVisaoController(InstanciasVisaoService service) {
        this.service = service;
    }

    @GetMapping("/instancias")
    @PreAuthorize("hasAuthority('ROLE_OPERADOR')")
    public InstanciasVisaoService.Pagina visao(@RequestParam(required = false) InstanciasVisaoService.Nivel nivel,
                                               @RequestParam(defaultValue = "0") int pagina) {
        return service.visao(nivel, pagina);
    }

    @PostMapping("/instancias/atualizacao")
    @PreAuthorize("hasAuthority('ROLE_OPERADOR')")
    public InstanciasVisaoService.ResultadoVarredura atualizar() {
        return service.varrer();
    }
}
