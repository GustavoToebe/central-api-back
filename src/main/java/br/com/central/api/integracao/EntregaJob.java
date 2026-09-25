package br.com.central.api.integracao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Varre o outbox a cada minuto. Nos testes o bean não sobe. */
@Component
@ConditionalOnProperty(prefix = "central.entrega.job", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EntregaJob {

    private static final Logger log = LoggerFactory.getLogger(EntregaJob.class);

    private final EntregaIntegracao entrega;

    public EntregaJob(EntregaIntegracao entrega) {
        this.entrega = entrega;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 15_000)
    public void enviar() {
        int enviados = entrega.enviarProntos();
        if (enviados > 0) {
            log.info("Outbox: {} evento(s) tratado(s).", enviados);
        }
    }
}
