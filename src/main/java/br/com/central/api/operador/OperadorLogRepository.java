package br.com.central.api.operador;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OperadorLogRepository extends JpaRepository<OperadorLog, UUID> {
}
