package br.com.central.api.integracao;

import br.com.central.api.comercial.TipoRecurso;
import br.com.central.api.comercial.dto.DireitosInstancia;

import java.time.Instant;
import java.util.List;

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
