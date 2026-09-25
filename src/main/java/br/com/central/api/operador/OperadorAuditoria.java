package br.com.central.api.operador;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Transação própria: o registro de login recusado sobrevive ao rollback do 401. */
@Service
public class OperadorAuditoria {

    private final OperadorLogRepository repository;

    public OperadorAuditoria(OperadorLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(UUID operadorId, String acao, String detalhe, String ip) {
        repository.save(new OperadorLog(operadorId, acao, detalhe, ip));
    }
}
