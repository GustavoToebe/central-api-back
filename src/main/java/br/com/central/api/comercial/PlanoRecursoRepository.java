package br.com.central.api.comercial;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PlanoRecursoRepository extends JpaRepository<PlanoRecurso, PlanoRecursoId> {

    @Query("select pr from PlanoRecurso pr join fetch pr.recurso where pr.planoId = :planoId")
    List<PlanoRecurso> listarDoPlano(@Param("planoId") UUID planoId);

    void deleteByPlanoId(UUID planoId);
}
