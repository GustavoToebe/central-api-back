package br.com.central.api.comercial;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AdicionalRepository extends JpaRepository<Adicional, UUID> {

    boolean existsByProduto_IdAndCodigo(UUID produtoId, String codigo);

    List<Adicional> findByProduto_IdOrderByNomeAsc(UUID produtoId);

    List<Adicional> findAllByOrderByNomeAsc();
}
