package br.com.central.api.comercial;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EventoSaidaRepository extends JpaRepository<EventoSaida, UUID> {

    List<EventoSaida> findByContratacaoIdAndSituacao(UUID contratacaoId, SituacaoEvento situacao);

    List<EventoSaida> findByContratacaoId(UUID contratacaoId);

    @Query("""
            select e.id from EventoSaida e
            where e.situacao = :situacao and e.proximaTentativa <= :agora
            order by e.proximaTentativa asc
            """)
    List<UUID> idsProntos(@Param("situacao") SituacaoEvento situacao, @Param("agora") Instant agora);
}
