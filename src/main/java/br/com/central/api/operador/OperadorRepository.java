package br.com.central.api.operador;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OperadorRepository extends JpaRepository<Operador, UUID> {

    Optional<Operador> findByEmail(String email);
}
