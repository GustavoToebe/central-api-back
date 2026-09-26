package br.com.central.api.comercial;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ContratacaoRepository extends JpaRepository<Contratacao, UUID> {

    boolean existsByProduto_IdAndSlugInstancia(UUID produtoId, String slug);

    List<Contratacao> findBySituacaoComercialIn(Collection<SituacaoComercial> situacoes);

    java.util.Optional<Contratacao> findByProduto_IdAndIdExterno(UUID produtoId, UUID idExterno);

    List<Contratacao> findAllByOrderByCriadoEmDesc();

    @Query("""
            select c from Contratacao c
            join fetch c.cliente
            join fetch c.produto
            join fetch c.plano
            where c.id = :id
            """)
    Contratacao buscarComReferencias(@Param("id") UUID id);

    @Query(
            value = """
                    select c from Contratacao c
                    where upper(c.produto.codigo) = upper(:codigo)
                      and c.idExterno is not null
                    """,
            countQuery = """
                    select count(c) from Contratacao c
                    where upper(c.produto.codigo) = upper(:codigo)
                      and c.idExterno is not null
                    """)
    Page<Contratacao> provisionadasDoProduto(@Param("codigo") String codigo, Pageable pageable);
}
