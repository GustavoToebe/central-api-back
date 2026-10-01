package br.com.central.api.comercial;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CobrancaRepository extends JpaRepository<Cobranca, UUID> {

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
