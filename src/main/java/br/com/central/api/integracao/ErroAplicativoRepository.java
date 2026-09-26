package br.com.central.api.integracao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

/** A gravação fica no {@link ErroAplicativoService} ({@code INSERT ... ON CONFLICT} com JDBC). */
public interface ErroAplicativoRepository extends JpaRepository<ErroAplicativo, UUID> {

    @Modifying
    @Query("delete from ErroAplicativo e where e.recebidoEm < :limite")
    int apagarRecebidosAntesDe(Instant limite);
}
