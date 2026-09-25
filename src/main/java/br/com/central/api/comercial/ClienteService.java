package br.com.central.api.comercial;

import br.com.central.api.comercial.dto.ClienteDtos.ClienteResponse;
import br.com.central.api.comercial.dto.ClienteDtos.ContatoRequest;
import br.com.central.api.comercial.dto.ClienteDtos.SalvarClienteRequest;
import br.com.central.api.web.ConflictException;
import br.com.central.api.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;

    public ClienteService(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    @Transactional(readOnly = true)
    public List<ClienteResponse> listar() {
        return clienteRepository.findAllByOrderByNomeAsc().stream().map(cliente -> {
            cliente.getContatos().size();
            return ClienteResponse.de(cliente);
        }).toList();
    }

    @Transactional(readOnly = true)
    public ClienteResponse buscar(UUID id) {
        return ClienteResponse.de(carregar(id));
    }

    @Transactional
    public ClienteResponse criar(SalvarClienteRequest request) {
        String documento = documento(request.documento());
        if (clienteRepository.existsByDocumento(documento)) {
            throw new ConflictException("Já existe um cliente com este documento.", "CONFLITO");
        }
        Cliente cliente = new Cliente(request.tipo(), documento, request.nome().trim());
        preencher(cliente, request);
        cliente.substituirContatos(contatos(request.contatos()));
        return ClienteResponse.de(clienteRepository.saveAndFlush(cliente));
    }

    @Transactional
    public ClienteResponse atualizar(UUID id, SalvarClienteRequest request) {
        Cliente cliente = carregar(id);
        String documento = documento(request.documento());
        if (clienteRepository.existsByDocumentoAndIdNot(documento, id)) {
            throw new ConflictException("Já existe um cliente com este documento.", "CONFLITO");
        }
        cliente.setTipo(request.tipo());
        cliente.setDocumento(documento);
        cliente.setNome(request.nome().trim());
        preencher(cliente, request);
        cliente.substituirContatos(contatos(request.contatos()));
        return ClienteResponse.de(cliente);
    }

    Cliente carregar(UUID id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado."));
        cliente.getContatos().size();
        return cliente;
    }

    private static void preencher(Cliente cliente, SalvarClienteRequest request) {
        cliente.setLogradouro(texto(request.logradouro()));
        cliente.setNumero(texto(request.numero()));
        cliente.setComplemento(texto(request.complemento()));
        cliente.setBairro(texto(request.bairro()));
        cliente.setCidade(texto(request.cidade()));
        cliente.setUf(texto(request.uf()));
        cliente.setCep(texto(request.cep()));
    }

    private static List<ClienteContato> contatos(List<ContatoRequest> pedidos) {
        if (pedidos == null) {
            return List.of();
        }
        return pedidos.stream()
                .map(pedido -> new ClienteContato(
                        pedido.nome().trim(), texto(pedido.email()), texto(pedido.telefone()), pedido.principal()))
                .toList();
    }

    private static String documento(String valor) {
        return valor.trim();
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
