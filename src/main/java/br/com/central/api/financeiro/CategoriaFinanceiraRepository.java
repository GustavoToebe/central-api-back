package br.com.central.api.financeiro;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface CategoriaFinanceiraRepository extends JpaRepository<CategoriaFinanceira, UUID> {
    List<CategoriaFinanceira> findAllByOrderByNomeAsc();
    boolean existsByNomeIgnoreCase(String nome);
}
