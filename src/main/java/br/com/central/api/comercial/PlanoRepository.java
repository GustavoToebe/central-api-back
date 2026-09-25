package br.com.central.api.comercial;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlanoRepository extends JpaRepository<Plano, UUID> {

    boolean existsByProduto_IdAndCodigo(UUID produtoId, String codigo);

    List<Plano> findByProduto_IdOrderByNomeAsc(UUID produtoId);

    List<Plano> findAllByOrderByNomeAsc();
}
