package br.com.central.api.comercial;

import br.com.central.api.comercial.dto.ClienteDtos.ClienteResponse;
import br.com.central.api.comercial.dto.ClienteDtos.SalvarClienteRequest;
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
@RequestMapping("/clientes")
@PreAuthorize("hasAuthority('ROLE_OPERADOR')")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @GetMapping
    public List<ClienteResponse> listar() {
        return clienteService.listar();
    }

    @GetMapping("/{id}")
    public ClienteResponse buscar(@PathVariable UUID id) {
        return clienteService.buscar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClienteResponse criar(@Valid @RequestBody SalvarClienteRequest request) {
        return clienteService.criar(request);
    }

    @PutMapping("/{id}")
    public ClienteResponse atualizar(@PathVariable UUID id, @Valid @RequestBody SalvarClienteRequest request) {
        return clienteService.atualizar(id, request);
    }
}
