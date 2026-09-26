package br.com.central.api.comercial;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProdutoRepository extends JpaRepository<Produto, UUID> {

    boolean existsByCodigo(String codigo);

    java.util.Optional<Produto> findByCodigoIgnoreCase(String codigo);

    List<Produto> findAllByOrderByNomeAsc();
}
