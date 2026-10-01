package br.com.central.api.financeiro;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import java.util.*;
import java.time.LocalDate;
import static br.com.central.api.financeiro.FinanceiroDtos.*;
import static br.com.central.api.financeiro.MovimentoFinanceiro.*;

@RestController @RequestMapping("/financeiro")
public class FinanceiroController {
    private final FinanceiroService servico;
    public FinanceiroController(FinanceiroService servico) { this.servico=servico; }
    @GetMapping("/contas") @PreAuthorize("hasRole('OPERADOR')")
    public List<ContaResponse> contas() { return servico.contas(); }
    @PostMapping("/contas") @PreAuthorize("hasRole('OPERADOR')")
    public ContaResponse criarConta(@Valid @RequestBody ContaRequest req) { return servico.salvarConta(null,req); }
    @PutMapping("/contas/{id}") @PreAuthorize("hasRole('OPERADOR')")
    public ContaResponse alterarConta(@PathVariable UUID id,@Valid @RequestBody ContaRequest req) { return servico.salvarConta(id,req); }
    @GetMapping("/categorias") @PreAuthorize("hasRole('OPERADOR')")
    public List<CategoriaResponse> categorias() { return servico.categorias(); }
    @PostMapping("/categorias") @PreAuthorize("hasRole('OPERADOR')")
    public CategoriaResponse criarCategoria(@Valid @RequestBody CategoriaRequest req) { return servico.salvarCategoria(null,req); }
    @PutMapping("/categorias/{id}") @PreAuthorize("hasRole('OPERADOR')")
    public CategoriaResponse alterarCategoria(@PathVariable UUID id,@Valid @RequestBody CategoriaRequest req) { return servico.salvarCategoria(id,req); }
    @GetMapping("/movimentos") @PreAuthorize("hasRole('OPERADOR')")
    public Pagina listar(@RequestParam LocalDate de,@RequestParam LocalDate ate,@RequestParam(required=false) String nome,
          @RequestParam(required=false) UUID contaId,@RequestParam(required=false) UUID categoriaId,
          @RequestParam(required=false) Situacao situacao,@RequestParam(required=false) Tipo tipo,
          @RequestParam(defaultValue="0") int pagina,@RequestParam(defaultValue="30") int tamanho) {
        return servico.listar(de,ate,nome,contaId,categoriaId,situacao,tipo,pagina,tamanho);
    }
    @PostMapping("/movimentos") @PreAuthorize("hasRole('OPERADOR')")
    public MovimentoResponse criar(@Valid @RequestBody MovimentoRequest req) { return servico.salvarMovimento(null,req); }
    @PutMapping("/movimentos/{id}") @PreAuthorize("hasRole('OPERADOR')")
    public MovimentoResponse alterar(@PathVariable UUID id,@Valid @RequestBody MovimentoRequest req) { return servico.salvarMovimento(id,req); }
    @PostMapping("/movimentos/{id}/baixar") @PreAuthorize("hasRole('OPERADOR')")
    public MovimentoResponse baixar(@PathVariable UUID id,@Valid @RequestBody BaixaRequest req) { return servico.baixar(id,req); }
    @PostMapping("/movimentos/{id}/estornar") @PreAuthorize("hasRole('OPERADOR')")
    public MovimentoResponse estornar(@PathVariable UUID id,@Valid @RequestBody VersaoRequest req) { return servico.estornar(id,req); }
    @PostMapping("/movimentos/{id}/cancelar") @PreAuthorize("hasRole('OPERADOR')")
    public MovimentoResponse cancelar(@PathVariable UUID id,@Valid @RequestBody VersaoRequest req) { return servico.cancelar(id,req); }
    @GetMapping("/resumo") @PreAuthorize("hasRole('OPERADOR')")
    public Resumo resumo(@RequestParam LocalDate de,@RequestParam LocalDate ate) { return servico.resumo(de,ate); }
}
