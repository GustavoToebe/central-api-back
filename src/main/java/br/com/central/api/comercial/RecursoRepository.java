package br.com.central.api.comercial;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecursoRepository extends JpaRepository<Recurso, UUID> {

    boolean existsByProduto_IdAndCodigo(UUID produtoId, String codigo);

    List<Recurso> findByProduto_IdOrderByCodigoAsc(UUID produtoId);

    List<Recurso> findAllByOrderByCodigoAsc();
}
