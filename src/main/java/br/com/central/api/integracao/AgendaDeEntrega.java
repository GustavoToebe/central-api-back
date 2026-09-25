package br.com.central.api.integracao;

import java.time.Duration;
import java.time.Instant;

/**
 * Duas agendas, porque o contrato e a arquitetura pedem prazos diferentes.
 * Provisionar (POST) espera 1 min, 5 min, 15 min e 1 h e então para: o
 * operador usa o botão. O webhook de direitos (PUT) segue até 72 h a partir
 * da criação do evento: 1 min, 5 min, 15 min, 1 h, 3 h e depois a cada 6 h.
 * Uma espera que cairia depois das 72 h não é agendada.
 */
public final class AgendaDeEntrega {

    public enum Tipo {
        PROVISIONAMENTO,
        WEBHOOK
    }

    public record Decisao(boolean parar, Instant quando) {
    }

    private static final Duration[] PROVISIONAMENTO = {
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(15),
            Duration.ofHours(1)
    };

    private static final Duration[] WEBHOOK = {
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(15),
            Duration.ofHours(1),
            Duration.ofHours(3)
    };

    private static final Duration LIMITE_WEBHOOK = Duration.ofHours(72);

    private AgendaDeEntrega() {
    }

    public static Decisao decidir(Tipo tipo, int tentativas, Instant criadoEm, Instant agora) {
        if (tipo == Tipo.PROVISIONAMENTO) {
            if (tentativas >= PROVISIONAMENTO.length) {
                return new Decisao(true, null);
            }
            return new Decisao(false, agora.plus(PROVISIONAMENTO[tentativas]));
        }
        Duration espera = tentativas < WEBHOOK.length ? WEBHOOK[tentativas] : Duration.ofHours(6);
        Instant limite = criadoEm.plus(LIMITE_WEBHOOK);
        Instant quando = agora.plus(espera);
        if (agora.isAfter(limite) || quando.isAfter(limite)) {
            return new Decisao(true, null);
        }
        return new Decisao(false, quando);
    }
}
