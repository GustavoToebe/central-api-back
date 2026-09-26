package br.com.central.api.integracao;

import br.com.central.api.comercial.TipoRecurso;
import br.com.central.api.comercial.dto.DireitosInstancia;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class IntegracaoDtos {

    private IntegracaoDtos() {
    }

    public record PaginaDireitos(
            List<DireitosInstancia> itens,
            int pagina,
            int tamanho,
            long total,
            Instant geradoEm
    ) {
    }

    public record SuporteResponse(String codigo, String urlAcesso, Instant expiraEm) {
    }

    /** Um erro de servidor do app (contrato 6.2). Só dado técnico; usuário pelo id. */
    public record ErroRecebido(
            @NotNull(message = "Informe o id do erro.") UUID id,
            @NotNull(message = "Informe quando ocorreu.") Instant ocorridoEm,
            UUID tenantId,
            UUID usuarioId,
            String metodo,
            String rota,
            @NotNull(message = "Informe o status.") Integer status,
            String codigo,
            String mensagem,
            String requestId
    ) {
    }

    public record LoteDeErros(
            @NotNull(message = "Informe os erros.")
            @Size(max = 100, message = "Mande no máximo 100 erros por vez.") List<@Valid ErroRecebido> erros
    ) {
    }

    public record ErrosRecebidos(int recebidos, int gravados) {
    }

    /** Linha da tela "Logs": o erro com a contratação (quando o app informou a instância). */
    public record ErroLinha(
            UUID id,
            Instant ocorridoEm,
            String produtoCodigo,
            UUID contratacaoId,
            String clienteNome,
            String nomeInstancia,
            UUID tenantId,
            UUID usuarioId,
            String metodo,
            String rota,
            int status,
            String codigo,
            String mensagem,
            String requestId
    ) {
    }

    /** Todos opcionais; {@code de}/{@code ate} pela data em que ocorreu (fuso de Brasília). */
    public record FiltroErros(
            UUID produtoId,
            UUID contratacaoId,
            LocalDate de,
            LocalDate ate,
            String busca
    ) {
    }

    /**
     * Um item do catálogo do app (contrato 5.5). {@code cadastrado} = já existe
     * recurso com este código exato no produto; {@code aplicado} = o app já faz valer.
     */
    public record RecursoDoAppResponse(
            String codigo,
            String nome,
            TipoRecurso tipo,
            String unidade,
            boolean aplicado,
            boolean cadastrado
    ) {
    }
}
