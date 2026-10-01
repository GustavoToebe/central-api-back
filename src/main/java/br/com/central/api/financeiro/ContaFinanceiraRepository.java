package br.com.central.api.financeiro;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.UUID;
public interface ContaFinanceiraRepository extends JpaRepository<ContaFinanceira, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ContaFinanceira c where c.id=:id")
    Optional<ContaFinanceira> buscarParaAlterar(@Param("id") UUID id);
    List<ContaFinanceira> findAllByOrderByNomeAsc();
    boolean existsByNomeIgnoreCase(String nome);
}
