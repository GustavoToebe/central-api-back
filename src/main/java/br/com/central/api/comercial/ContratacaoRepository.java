package br.com.central.api.comercial;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ContratacaoRepository extends JpaRepository<Contratacao, UUID> {

    boolean existsByProduto_IdAndSlugInstancia(UUID produtoId, String slug);

    List<Contratacao> findBySituacaoComercialIn(Collection<SituacaoComercial> situacoes);

    List<Contratacao> findAllByOrderByCriadoEmDesc();

    @Query("""
            select c from Contratacao c
            join fetch c.cliente
            join fetch c.produto
            join fetch c.plano
            where c.id = :id
            """)
    Contratacao buscarComReferencias(@Param("id") UUID id);
}
