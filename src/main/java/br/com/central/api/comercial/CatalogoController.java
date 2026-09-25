package br.com.central.api.comercial;

import br.com.central.api.comercial.dto.CatalogoDtos.AdicionalResponse;
import br.com.central.api.comercial.dto.CatalogoDtos.NovoPrecoRequest;
import br.com.central.api.comercial.dto.CatalogoDtos.PlanoResponse;
import br.com.central.api.comercial.dto.CatalogoDtos.ProdutoResponse;
import br.com.central.api.comercial.dto.CatalogoDtos.RecursoResponse;
import br.com.central.api.comercial.dto.CatalogoDtos.SalvarAdicionalRequest;
import br.com.central.api.comercial.dto.CatalogoDtos.SalvarPlanoRequest;
import br.com.central.api.comercial.dto.CatalogoDtos.SalvarProdutoRequest;
import br.com.central.api.comercial.dto.CatalogoDtos.SalvarRecursoRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@PreAuthorize("hasAuthority('ROLE_OPERADOR')")
public class CatalogoController {

    private final CatalogoService catalogoService;

    public CatalogoController(CatalogoService catalogoService) {
        this.catalogoService = catalogoService;
    }

    @GetMapping("/produtos")
    public List<ProdutoResponse> listarProdutos() {
        return catalogoService.listarProdutos();
    }

    @PostMapping("/produtos")
    @ResponseStatus(HttpStatus.CREATED)
    public ProdutoResponse criarProduto(@Valid @RequestBody SalvarProdutoRequest request) {
        return catalogoService.criarProduto(request);
    }

    @PutMapping("/produtos/{id}")
    public ProdutoResponse atualizarProduto(@PathVariable UUID id, @Valid @RequestBody SalvarProdutoRequest request) {
        return catalogoService.atualizarProduto(id, request);
    }

    @GetMapping("/recursos")
    public List<RecursoResponse> listarRecursos(@RequestParam(required = false) UUID produtoId) {
        return catalogoService.listarRecursos(produtoId);
    }

    @PostMapping("/recursos")
    @ResponseStatus(HttpStatus.CREATED)
    public RecursoResponse criarRecurso(@Valid @RequestBody SalvarRecursoRequest request) {
        return catalogoService.criarRecurso(request);
    }

    @PutMapping("/recursos/{id}")
    public RecursoResponse atualizarRecurso(@PathVariable UUID id, @Valid @RequestBody SalvarRecursoRequest request) {
        return catalogoService.atualizarRecurso(id, request);
    }

    @GetMapping("/planos")
    public List<PlanoResponse> listarPlanos(@RequestParam(required = false) UUID produtoId) {
        return catalogoService.listarPlanos(produtoId);
    }

    @PostMapping("/planos")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanoResponse criarPlano(@Valid @RequestBody SalvarPlanoRequest request) {
        return catalogoService.criarPlano(request);
    }

    @PutMapping("/planos/{id}")
    public PlanoResponse atualizarPlano(@PathVariable UUID id, @Valid @RequestBody SalvarPlanoRequest request) {
        return catalogoService.atualizarPlano(id, request);
    }

    @PostMapping("/planos/{id}/precos")
    public PlanoResponse adicionarPreco(@PathVariable UUID id, @Valid @RequestBody NovoPrecoRequest request) {
        return catalogoService.adicionarPreco(id, request);
    }

    @GetMapping("/adicionais")
    public List<AdicionalResponse> listarAdicionais(@RequestParam(required = false) UUID produtoId) {
        return catalogoService.listarAdicionais(produtoId);
    }

    @PostMapping("/adicionais")
    @ResponseStatus(HttpStatus.CREATED)
    public AdicionalResponse criarAdicional(@Valid @RequestBody SalvarAdicionalRequest request) {
        return catalogoService.criarAdicional(request);
    }

    @PutMapping("/adicionais/{id}")
    public AdicionalResponse atualizarAdicional(@PathVariable UUID id,
                                                 @Valid @RequestBody SalvarAdicionalRequest request) {
        return catalogoService.atualizarAdicional(id, request);
    }
}
