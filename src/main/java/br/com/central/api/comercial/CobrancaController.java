package br.com.central.api.comercial;

import br.com.central.api.comercial.dto.FinanceiroDtos.CobrancaDetalhe;
import br.com.central.api.comercial.dto.FinanceiroDtos.CobrancaLinha;
import br.com.central.api.comercial.dto.FinanceiroDtos.FiltroCobrancas;
import br.com.central.api.comercial.dto.FinanceiroDtos.RegistrarPagamentoRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Tela "Cobranças" (26/09/2026): todas as cobranças com filtros e pagamento
 * de várias de uma vez, mesmo de clientes diferentes.
 */
@RestController
@RequestMapping("/cobrancas")
@PreAuthorize("hasAuthority('ROLE_OPERADOR')")
public class CobrancaController {

    private final BillingService billingService;

    public CobrancaController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping
    public List<CobrancaLinha> listar(
            @RequestParam(required = false) UUID produtoId,
            @RequestParam(required = false) String situacao,
            @RequestParam(required = false) FormaPagamento formaPagamento,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimentoDe,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimentoAte,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth competencia,
            @RequestParam(required = false) String busca) {
        return billingService.listar(new FiltroCobrancas(
                produtoId, situacao, formaPagamento, vencimentoDe, vencimentoAte, competencia, busca));
    }

    @GetMapping("/{id}")
    public CobrancaDetalhe detalhe(@PathVariable UUID id) {
        return billingService.detalhe(id);
    }

    @PostMapping("/pagamentos")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registrarPagamentos(@Valid @RequestBody RegistrarPagamentoRequest request) {
        billingService.registrarPagamentos(request);
    }
}
