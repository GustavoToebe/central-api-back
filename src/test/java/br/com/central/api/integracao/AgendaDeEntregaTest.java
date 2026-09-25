package br.com.central.api.integracao;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AgendaDeEntregaTest {

    private static final Instant CRIADO = Instant.parse("2026-06-01T12:00:00Z");

    @Test
    void provisionamentoParaDepoisDeUmaHora() {
        assertThat(espera(AgendaDeEntrega.Tipo.PROVISIONAMENTO, 0)).isEqualTo(Duration.ofMinutes(1));
        assertThat(espera(AgendaDeEntrega.Tipo.PROVISIONAMENTO, 1)).isEqualTo(Duration.ofMinutes(5));
        assertThat(espera(AgendaDeEntrega.Tipo.PROVISIONAMENTO, 2)).isEqualTo(Duration.ofMinutes(15));
        assertThat(espera(AgendaDeEntrega.Tipo.PROVISIONAMENTO, 3)).isEqualTo(Duration.ofHours(1));
        assertThat(AgendaDeEntrega.decidir(AgendaDeEntrega.Tipo.PROVISIONAMENTO, 4, CRIADO, CRIADO).parar()).isTrue();
    }

    @Test
    void webhookSegueAte72Horas() {
        assertThat(espera(AgendaDeEntrega.Tipo.WEBHOOK, 0)).isEqualTo(Duration.ofMinutes(1));
        assertThat(espera(AgendaDeEntrega.Tipo.WEBHOOK, 3)).isEqualTo(Duration.ofHours(1));
        assertThat(espera(AgendaDeEntrega.Tipo.WEBHOOK, 4)).isEqualTo(Duration.ofHours(3));
        assertThat(espera(AgendaDeEntrega.Tipo.WEBHOOK, 5)).isEqualTo(Duration.ofHours(6));

        Instant quase = CRIADO.plus(Duration.ofHours(70));
        assertThat(AgendaDeEntrega.decidir(AgendaDeEntrega.Tipo.WEBHOOK, 5, CRIADO, quase).parar()).isTrue();

        Instant dentro = CRIADO.plus(Duration.ofHours(71));
        AgendaDeEntrega.Decisao ainda = AgendaDeEntrega.decidir(AgendaDeEntrega.Tipo.WEBHOOK, 0, CRIADO, dentro);
        assertThat(ainda.parar()).isFalse();
        assertThat(ainda.quando()).isEqualTo(dentro.plus(Duration.ofMinutes(1)));
    }

    private static Duration espera(AgendaDeEntrega.Tipo tipo, int tentativas) {
        AgendaDeEntrega.Decisao decisao = AgendaDeEntrega.decidir(tipo, tentativas, CRIADO, CRIADO);
        assertThat(decisao.parar()).isFalse();
        return Duration.between(CRIADO, decisao.quando());
    }
}
