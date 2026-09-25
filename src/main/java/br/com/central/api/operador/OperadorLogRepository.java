package br.com.central.api.operador;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OperadorLogRepository extends JpaRepository<OperadorLog, UUID> {

    List<OperadorLog> findByAcao(String acao);
}
