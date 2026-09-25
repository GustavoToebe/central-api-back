package br.com.central.api.integracao;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Clock;
import java.util.function.Supplier;

/**
 * Cliente de saída. Cada envio ganha horário, nonce e assinatura novos.
 * A {@code Idempotency-Key}, quando existe, é repetida igual — quem chama
 * decide o valor. O caminho assinado é o path do contrato, sem o host.
 */
public class AplicativoHttp {

    public record Resposta(int status, byte[] corpo) {
    }

    private final RestClient http;
    private final String chaveId;
    private final byte[] segredo;
    private final Clock clock;
    private final Supplier<String> nonces;

    public AplicativoHttp(RestClient http, String chaveId, byte[] segredo, Clock clock, Supplier<String> nonces) {
        this.http = http;
        this.chaveId = chaveId;
        this.segredo = segredo;
        this.clock = clock;
        this.nonces = nonces;
    }

    public Resposta enviar(String urlBase, String metodo, String caminho, byte[] corpo, String idempotencyKey) {
        if (urlBase == null || urlBase.isBlank()) {
            throw new IllegalStateException("Produto sem URL de integração.");
        }
        if (chaveId == null || chaveId.isBlank() || segredo == null || segredo.length == 0) {
            throw new IllegalStateException("Chave de saída da integração não configurada.");
        }
        byte[] bytes = corpo == null ? new byte[0] : corpo;
        String timestamp = Long.toString(clock.instant().getEpochSecond());
        String nonce = nonces.get();
        String assinatura = HmacAssinatura.assinar(segredo, metodo, caminho, timestamp, nonce, bytes);
        String base = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase;
        try {
            return http.method(HttpMethod.valueOf(metodo))
                    .uri(URI.create(base + caminho))
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        headers.set(IntegracaoFiltro.CHAVE, chaveId);
                        headers.set(IntegracaoFiltro.TIMESTAMP, timestamp);
                        headers.set(IntegracaoFiltro.NONCE, nonce);
                        headers.set(IntegracaoFiltro.ASSINATURA, assinatura);
                        if (idempotencyKey != null) {
                            headers.set("Idempotency-Key", idempotencyKey);
                        }
                    })
                    .body(bytes)
                    .exchange((request, response) -> new Resposta(
                            response.getStatusCode().value(),
                            ler(response.getBody())));
        } catch (RestClientException e) {
            throw new IllegalStateException("Falha de rede ao falar com o aplicativo.", e);
        }
    }

    private static byte[] ler(InputStream corpo) {
        if (corpo == null) {
            return new byte[0];
        }
        try {
            return corpo.readAllBytes();
        } catch (java.io.IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
