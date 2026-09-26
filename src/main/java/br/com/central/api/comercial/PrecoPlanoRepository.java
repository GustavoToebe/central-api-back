package br.com.central.api.comercial;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrecoPlanoRepository extends JpaRepository<PrecoPlano, UUID> {

    boolean existsByPlanoIdAndPeriodicidadeAndVigenteDesde(UUID planoId, Periodicidade periodicidade,
                                                           LocalDate vigenteDesde);

    Optional<PrecoPlano> findByPlanoIdAndPeriodicidadeAndVigenteDesde(UUID planoId, Periodicidade periodicidade,
                                                                      LocalDate vigenteDesde);

    Optional<PrecoPlano> findFirstByPlanoIdAndPeriodicidadeAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(
            UUID planoId, Periodicidade periodicidade, LocalDate data);

    List<PrecoPlano> findByPlanoIdOrderByVigenteDesdeDesc(UUID planoId);
}
