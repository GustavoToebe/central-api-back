package br.com.central.api.integracao;

import br.com.central.api.integracao.IntegracaoDtos.ErroLinha;
import br.com.central.api.integracao.IntegracaoDtos.FiltroErros;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Tela "Logs" e aba "Erros" da contratação (26/09/2026). */
@RestController
@PreAuthorize("hasAuthority('ROLE_OPERADOR')")
public class ErroAplicativoController {

    private final ErroAplicativoService service;

    public ErroAplicativoController(ErroAplicativoService service) {
        this.service = service;
    }

    @GetMapping("/erros")
    public List<ErroLinha> listar(
            @RequestParam(required = false) UUID produtoId,
            @RequestParam(required = false) UUID contratacaoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) String busca) {
        return service.listar(new FiltroErros(produtoId, contratacaoId, de, ate, busca));
    }
}
