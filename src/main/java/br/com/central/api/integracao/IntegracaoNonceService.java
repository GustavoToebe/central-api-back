package br.com.central.api.integracao;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class IntegracaoNonceService {

    private final IntegracaoNonceRepository repository;

    public IntegracaoNonceService(IntegracaoNonceRepository repository) {
        this.repository = repository;
    }

    /** Persiste mesmo se o handler seguinte falhar. {@code false} = nonce já usado. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean registrar(String chaveId, String nonce) {
        return repository.registrar(chaveId, nonce) == 1;
    }

    @Scheduled(fixedDelay = 600_000)
    @Transactional
    public void limpar() {
        repository.apagarAnterioresA(Instant.now().minus(10, ChronoUnit.MINUTES));
    }
}
