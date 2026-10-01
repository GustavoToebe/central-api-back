package br.com.central.api.operador;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface OperadorRepository extends JpaRepository<Operador, UUID> {

    Optional<Operador> findByEmail(String email);

    /** Serializa credenciais sem bloquear a FK da auditoria REQUIRES_NEW. */
    @Query(value = "select * from operador where email = :email for no key update", nativeQuery = true)
    Optional<Operador> buscarParaAutenticar(String email);

    @Query(value = "select * from operador where id = :id for no key update", nativeQuery = true)
    Optional<Operador> buscarParaAlterar(UUID id);
}
