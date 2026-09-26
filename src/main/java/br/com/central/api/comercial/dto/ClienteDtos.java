package br.com.central.api.comercial.dto;

import br.com.central.api.comercial.Cliente;
import br.com.central.api.comercial.ClienteContato;
import br.com.central.api.comercial.TipoCliente;
import br.com.central.api.web.Formatos;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public final class ClienteDtos {

    private ClienteDtos() {
    }

    public record ContatoRequest(
            @NotBlank(message = "Informe o nome do contato.") String nome,
            @Email(regexp = Formatos.EMAIL, message = "E-mail do contato inválido.") String email,
            String telefone,
            boolean principal
    ) {
    }

    public record SalvarClienteRequest(
            @NotNull(message = "Informe se o cliente é PF ou PJ.") TipoCliente tipo,
            @NotBlank(message = "Informe o documento.") String documento,
            @NotBlank(message = "Informe o nome.") String nome,
            String logradouro,
            String numero,
            String complemento,
            String bairro,
            String cidade,
            String uf,
            String cep,
            List<@Valid ContatoRequest> contatos
    ) {
    }

    public record ContatoResponse(UUID id, String nome, String email, String telefone, boolean principal) {
        static ContatoResponse de(ClienteContato contato) {
            return new ContatoResponse(contato.getId(), contato.getNome(), contato.getEmail(),
                    contato.getTelefone(), contato.isPrincipal());
        }
    }

    public record ClienteResponse(
            UUID id,
            Long sequencial,
            TipoCliente tipo,
            String documento,
            String nome,
            String logradouro,
            String numero,
            String complemento,
            String bairro,
            String cidade,
            String uf,
            String cep,
            List<ContatoResponse> contatos
    ) {
        public static ClienteResponse de(Cliente cliente) {
            return new ClienteResponse(
                    cliente.getId(), cliente.getSequencial(), cliente.getTipo(), cliente.getDocumento(), cliente.getNome(),
                    cliente.getLogradouro(), cliente.getNumero(), cliente.getComplemento(),
                    cliente.getBairro(), cliente.getCidade(), cliente.getUf(), cliente.getCep(),
                    cliente.getContatos().stream().map(ContatoResponse::de).toList());
        }
    }
}
