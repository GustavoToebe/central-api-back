package br.com.central.api.comercial;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventoSaidaRepository extends JpaRepository<EventoSaida, UUID> {

    List<EventoSaida> findByContratacaoIdAndSituacao(UUID contratacaoId, SituacaoEvento situacao);

    List<EventoSaida> findByContratacaoId(UUID contratacaoId);

    @Query("""
            select e.id from EventoSaida e
            where e.situacao = :situacao and e.proximaTentativa <= :agora
              and (e.reservaAte is null or e.reservaAte <= :agora)
            order by e.proximaTentativa asc, e.id asc
            """)
    List<UUID> idsProntos(@Param("situacao") SituacaoEvento situacao, @Param("agora") Instant agora, Pageable pagina);
    @Query("select e.contratacaoId from EventoSaida e where e.id=:id")
    Optional<UUID> contratacaoDoEvento(@Param("id") UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EventoSaida e where e.id = :id")
    Optional<EventoSaida> buscarParaAlterar(@Param("id") UUID id);

    boolean existsByContratacaoIdAndReservaAteAfter(UUID contratacaoId, Instant agora);
}
