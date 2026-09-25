package br.com.central.api.comercial;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Todo dia às 03:00 (Brasília) gera cobranças e marca INADIMPLENTE.
 * Não bloqueia: bloqueio é ação do operador (decisão 16).
 */
@Component
@ConditionalOnProperty(prefix = "central.billing.job", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BillingJob {

    private static final Logger log = LoggerFactory.getLogger(BillingJob.class);

    private final BillingService billingService;

    public BillingJob(BillingService billingService) {
        this.billingService = billingService;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "America/Sao_Paulo")
    public void gerarCobrancas() {
        int geradas = billingService.gerarCobrancasDeTodas();
        log.info("Job de cobranças: {} cobrança(s) nova(s) gerada(s).", geradas);
    }
}
