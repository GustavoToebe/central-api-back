package br.com.central.api.comercial;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ContratacaoAdicionalRepository extends JpaRepository<ContratacaoAdicional, UUID> {

    @Query("""
            select ca from ContratacaoAdicional ca
            join fetch ca.adicional a
            join fetch a.recurso
            where ca.contratacao.id = :contratacaoId
            """)
    List<ContratacaoAdicional> listarDaContratacao(@Param("contratacaoId") UUID contratacaoId);

    void deleteByContratacao_Id(UUID contratacaoId);
}
