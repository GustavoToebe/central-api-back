package br.com.central.api.comercial;

import br.com.central.api.comercial.dto.ContratacaoDtos.AlterarPlanoRequest;
import br.com.central.api.comercial.dto.ContratacaoDtos.AtualizarProvisionamentoRequest;
import br.com.central.api.comercial.dto.ContratacaoDtos.ContratacaoResponse;
import br.com.central.api.comercial.dto.ContratacaoDtos.ContratacaoResumo;
import br.com.central.api.comercial.dto.ContratacaoDtos.CriarContratacaoRequest;
import br.com.central.api.comercial.dto.ContratacaoDtos.MotivoRequest;
import br.com.central.api.comercial.dto.ContratacaoDtos.SubstituirAdicionaisRequest;
import br.com.central.api.comercial.dto.FinanceiroDtos.FinanceiroResponse;
import br.com.central.api.comercial.dto.FinanceiroDtos.GerarCobrancasRequest;
import br.com.central.api.comercial.dto.FinanceiroDtos.IsentarCobrancaRequest;
import br.com.central.api.comercial.dto.FinanceiroDtos.RegistrarPagamentoRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/contratacoes")
@PreAuthorize("hasAuthority('ROLE_OPERADOR')")
public class ContratacaoController {

    private final ContratacaoService contratacaoService;
    private final BillingService billingService;

    public ContratacaoController(ContratacaoService contratacaoService, BillingService billingService) {
        this.contratacaoService = contratacaoService;
        this.billingService = billingService;
    }

    @GetMapping
    public List<ContratacaoResumo> listar() {
        return contratacaoService.listar();
    }

    @GetMapping("/{id}")
    public ContratacaoResponse buscar(@PathVariable UUID id) {
        return contratacaoService.buscar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContratacaoResponse criar(@Valid @RequestBody CriarContratacaoRequest request) {
        return contratacaoService.criar(request);
    }

    @PutMapping("/{id}")
    public ContratacaoResponse atualizarProvisionamento(@PathVariable UUID id,
                                                        @Valid @RequestBody AtualizarProvisionamentoRequest request) {
        return contratacaoService.atualizarProvisionamento(id, request);
    }

    @PutMapping("/{id}/plano")
    public ContratacaoResponse alterarPlano(@PathVariable UUID id, @Valid @RequestBody AlterarPlanoRequest request) {
        return contratacaoService.alterarPlano(id, request);
    }

    @PutMapping("/{id}/adicionais")
    public ContratacaoResponse substituirAdicionais(@PathVariable UUID id,
                                                    @Valid @RequestBody SubstituirAdicionaisRequest request) {
        return contratacaoService.substituirAdicionais(id, request);
    }

    @PostMapping("/{id}/bloquear")
    public ContratacaoResponse bloquear(@PathVariable UUID id, @Valid @RequestBody MotivoRequest request) {
        return contratacaoService.bloquear(id, request.motivo());
    }

    @PostMapping("/{id}/desbloquear")
    public ContratacaoResponse desbloquear(@PathVariable UUID id, @RequestBody(required = false) MotivoRequest request) {
        return contratacaoService.desbloquear(id, request == null ? null : request.motivo());
    }

    @PostMapping("/{id}/cancelar")
    public ContratacaoResponse cancelar(@PathVariable UUID id, @RequestBody(required = false) MotivoRequest request) {
        return contratacaoService.cancelar(id, request == null ? null : request.motivo());
    }

    @GetMapping("/{id}/financeiro")
    public FinanceiroResponse financeiro(@PathVariable UUID id) {
        return billingService.financeiro(id);
    }

    @PostMapping("/{id}/cobrancas/adiantadas")
    public FinanceiroResponse gerarAdiantadas(@PathVariable UUID id,
                                              @Valid @RequestBody GerarCobrancasRequest request) {
        return billingService.gerarAdiantadas(id, request.ate());
    }

    @PostMapping("/{id}/pagamentos")
    public FinanceiroResponse registrarPagamento(@PathVariable UUID id,
                                                 @Valid @RequestBody RegistrarPagamentoRequest request) {
        return billingService.registrarPagamento(id, request);
    }

    @PostMapping("/{id}/cobrancas/{cobrancaId}/estornar")
    public FinanceiroResponse estornar(@PathVariable UUID id, @PathVariable UUID cobrancaId) {
        return billingService.estornar(id, cobrancaId);
    }

    @PostMapping("/{id}/cobrancas/{cobrancaId}/isentar")
    public FinanceiroResponse isentar(@PathVariable UUID id, @PathVariable UUID cobrancaId,
                                      @RequestBody(required = false) IsentarCobrancaRequest request) {
        return billingService.isentar(id, cobrancaId, request == null ? null : request.motivo());
    }
}
