package br.com.central.api.comercial;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HistoricoContratacaoRepository extends JpaRepository<HistoricoContratacao, UUID> {

    List<HistoricoContratacao> findByContratacaoIdOrderByCriadoEmAsc(UUID contratacaoId);
}
