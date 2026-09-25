package br.com.central.api.integracao;

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
}
