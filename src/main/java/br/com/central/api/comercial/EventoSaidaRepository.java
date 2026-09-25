package br.com.central.api.comercial;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventoSaidaRepository extends JpaRepository<EventoSaida, UUID> {

    List<EventoSaida> findByContratacaoIdAndSituacao(UUID contratacaoId, SituacaoEvento situacao);

    List<EventoSaida> findByContratacaoId(UUID contratacaoId);
}
