package br.com.central.api.financeiro;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.persistence.LockModeType;

public interface MovimentoFinanceiroRepository extends JpaRepository<MovimentoFinanceiro, UUID>, JpaSpecificationExecutor<MovimentoFinanceiro> {
    @Override
    @EntityGraph(attributePaths={"conta","categoria"})
    org.springframework.data.domain.Page<MovimentoFinanceiro> findAll(org.springframework.data.jpa.domain.Specification<MovimentoFinanceiro> filtro, org.springframework.data.domain.Pageable pagina);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MovimentoFinanceiro m where m.id=:id")
    Optional<MovimentoFinanceiro> buscarParaAlterar(@Param("id") UUID id);
    boolean existsByConta_Id(UUID contaId);
    boolean existsByCategoria_Id(UUID categoriaId);
    interface TotalPorConta { UUID getContaId(); BigDecimal getReceitas(); BigDecimal getDespesas(); }
    @Query("""
        select m.conta.id as contaId,
          sum(case when m.tipo=br.com.central.api.financeiro.MovimentoFinanceiro.Tipo.RECEITA then m.valor else 0 end) as receitas,
          sum(case when m.tipo=br.com.central.api.financeiro.MovimentoFinanceiro.Tipo.DESPESA then m.valor else 0 end) as despesas
        from MovimentoFinanceiro m
        where m.situacao=br.com.central.api.financeiro.MovimentoFinanceiro.Situacao.PAGO
          and m.dataPagamento>=:de and m.dataPagamento<=:ate
        group by m.conta.id
        """)
    List<TotalPorConta> totais(@Param("de") LocalDate de, @Param("ate") LocalDate ate);

    @Query("""
        select coalesce(sum(case when m.tipo=br.com.central.api.financeiro.MovimentoFinanceiro.Tipo.RECEITA then m.valor else 0 end),0),
               coalesce(sum(case when m.tipo=br.com.central.api.financeiro.MovimentoFinanceiro.Tipo.DESPESA then m.valor else 0 end),0)
        from MovimentoFinanceiro m where m.situacao=br.com.central.api.financeiro.MovimentoFinanceiro.Situacao.PENDENTE
          and m.vencimento>=:de and m.vencimento<=:ate
        """)
    List<BigDecimal[]> previstos(@Param("de") LocalDate de, @Param("ate") LocalDate ate);
}
