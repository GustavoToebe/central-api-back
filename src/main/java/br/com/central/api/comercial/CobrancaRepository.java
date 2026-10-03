package br.com.central.api.comercial;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CobrancaRepository extends JpaRepository<Cobranca, UUID> {

    /** Cobranças pagas no período (pela data do pagamento), para o relatório de receitas. */
    @Query("select c from Cobranca c where c.status = :status and c.pagoEm >= :de and c.pagoEm <= :ate order by c.pagoEm, c.sequencial")
    List<Cobranca> pagasNoPeriodo(@Param("status") Cobranca.Status status, @Param("de") LocalDate de, @Param("ate") LocalDate ate);

    /** Cobranças em aberto com vencimento no período, para a visão prevista do relatório de receitas. */
    @Query("select c from Cobranca c where c.status = br.com.central.api.comercial.Cobranca.Status.ABERTA and c.vencimento >= :de and c.vencimento <= :ate order by c.vencimento, c.sequencial")
    List<Cobranca> abertasNoPeriodo(@Param("de") LocalDate de, @Param("ate") LocalDate ate);

    @Query("select coalesce(sum(c.valor),0) from Cobranca c where c.status=br.com.central.api.comercial.Cobranca.Status.ABERTA and c.vencimento>=:de and c.vencimento<=:ate")
    BigDecimal somarPrevisto(@Param("de") LocalDate de, @Param("ate") LocalDate ate);

    List<Cobranca> findByContratacaoId(UUID contratacaoId);

    List<Cobranca> findByContratacaoIdOrderByCompetenciaInicioDesc(UUID contratacaoId);

    List<Cobranca> findByContratacaoIdOrderByVencimentoDesc(UUID contratacaoId);

    List<Cobranca> findByIdInAndContratacaoId(List<UUID> ids, UUID contratacaoId);

    boolean existsByContratacaoIdAndStatusAndVencimentoBefore(UUID contratacaoId, Cobranca.Status status,
                                                              LocalDate data);

    @Query("""
            select c.contratacaoId, count(c), min(c.vencimento)
            from Cobranca c
            where c.status = :status and c.vencimento < :hoje
            group by c.contratacaoId
            """)
    List<Object[]> resumoAtraso(@Param("status") Cobranca.Status status, @Param("hoje") LocalDate hoje);

    @Query("""
            select count(distinct c.contratacaoId) from Cobranca c
            where c.status = :status and c.vencimento < :hoje
            """)
    long contarContratacoesEmAtraso(@Param("status") Cobranca.Status status, @Param("hoje") LocalDate hoje);

    @Query("""
            select coalesce(sum(c.valorPago), 0) from Cobranca c
            where c.status = :status and c.pagoEm >= :de and c.pagoEm <= :ate
            """)
    BigDecimal somarRecebido(@Param("status") Cobranca.Status status, @Param("de") LocalDate de,
                             @Param("ate") LocalDate ate);
}
