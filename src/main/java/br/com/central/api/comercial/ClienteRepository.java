package br.com.central.api.comercial;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClienteRepository extends JpaRepository<Cliente, UUID> {

    boolean existsByDocumento(String documento);

    boolean existsByDocumentoAndIdNot(String documento, UUID id);

    List<Cliente> findAllByOrderByNomeAsc();

    Optional<Cliente> findByDocumento(String documento);
}
