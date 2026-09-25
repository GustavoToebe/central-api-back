package br.com.central.api.integracao;

import br.com.central.api.comercial.dto.ContratacaoDtos.ContratacaoResponse;
import br.com.central.api.comercial.dto.ContratacaoDtos.MotivoRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/contratacoes")
@PreAuthorize("hasAuthority('ROLE_OPERADOR')")
public class ContratacaoIntegracaoController {

    private final EntregaIntegracao entrega;
    private final SuporteCentralService suporte;

    public ContratacaoIntegracaoController(EntregaIntegracao entrega, SuporteCentralService suporte) {
        this.entrega = entrega;
        this.suporte = suporte;
    }

    @PostMapping("/{id}/tentar-provisionamento")
    public ContratacaoResponse tentarProvisionamento(@PathVariable UUID id) {
        return entrega.tentarNovamente(id);
    }

    @PostMapping("/{id}/suporte")
    public IntegracaoDtos.SuporteResponse suporte(@PathVariable UUID id,
                                                  @Valid @RequestBody MotivoRequest request,
                                                  HttpServletRequest http) {
        return suporte.pedir(id, request.motivo(), http.getRemoteAddr());
    }
}
