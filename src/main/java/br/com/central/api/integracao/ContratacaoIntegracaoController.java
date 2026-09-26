package br.com.central.api.integracao;

import br.com.central.api.comercial.dto.ContratacaoDtos.ContratacaoResponse;
import br.com.central.api.comercial.dto.ContratacaoDtos.MotivoRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@PreAuthorize("hasAuthority('ROLE_OPERADOR')")
public class ContratacaoIntegracaoController {

    private final EntregaIntegracao entrega;
    private final SuporteCentralService suporte;
    private final CatalogoDoAppService catalogoDoApp;

    public ContratacaoIntegracaoController(EntregaIntegracao entrega, SuporteCentralService suporte,
                                           CatalogoDoAppService catalogoDoApp) {
        this.entrega = entrega;
        this.suporte = suporte;
        this.catalogoDoApp = catalogoDoApp;
    }

    /** Sugestões da tela de Recursos: o que o app entende em limites e funcionalidades. */
    @GetMapping("/produtos/{id}/recursos-do-app")
    public List<IntegracaoDtos.RecursoDoAppResponse> recursosDoApp(@PathVariable UUID id) {
        return catalogoDoApp.sugestoes(id);
    }

    @PostMapping("/contratacoes/{id}/tentar-provisionamento")
    public ContratacaoResponse tentarProvisionamento(@PathVariable UUID id) {
        return entrega.tentarNovamente(id);
    }

    @PostMapping("/contratacoes/{id}/suporte")
    public IntegracaoDtos.SuporteResponse suporte(@PathVariable UUID id,
                                                  @Valid @RequestBody MotivoRequest request,
                                                  HttpServletRequest http) {
        return suporte.pedir(id, request.motivo(), http.getRemoteAddr());
    }
}
