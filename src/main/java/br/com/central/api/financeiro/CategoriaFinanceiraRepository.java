package br.com.central.api.financeiro;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface CategoriaFinanceiraRepository extends JpaRepository<CategoriaFinanceira, UUID> {
    List<CategoriaFinanceira> findAllByOrderByNomeAsc();
    boolean existsByGrupoId(UUID grupoId);
    boolean existsByGrupoIdIsNullAndTipoAndNomeIgnoreCaseAndIdNot(MovimentoFinanceiro.Tipo tipo, String nome, UUID id);
    boolean existsByGrupoIdAndNomeIgnoreCaseAndIdNot(UUID grupoId, String nome, UUID id);
}
