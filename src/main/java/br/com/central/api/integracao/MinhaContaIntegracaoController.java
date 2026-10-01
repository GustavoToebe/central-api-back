package br.com.central.api.integracao;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/integracao/v1")
public class MinhaContaIntegracaoController {

    private final MinhaContaService minhaContaService;

    public MinhaContaIntegracaoController(MinhaContaService minhaContaService) {
        this.minhaContaService = minhaContaService;
    }

    @GetMapping("/produtos/{produto}/instancias/{idExterno}/minha-conta")
    @PreAuthorize("hasAuthority('PERM_INTEGRACAO')")
    public MinhaContaDto minhaConta(@PathVariable String produto, @PathVariable UUID idExterno) {
        return minhaContaService.obterMinhaConta(produto, idExterno);
    }
}
