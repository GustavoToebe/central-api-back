package br.com.central.api.operador;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Sobrevive ao 401 do reuso: a exceção seguinte não pode desfazer a revogação. */
@Service
public class RevogacaoDeSessao {

    private final RefreshTokenRepository repository;

    public RevogacaoDeSessao(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revogarTodos(UUID operadorId) {
        repository.revogarTodosAtivos(operadorId, Instant.now());
    }
}
