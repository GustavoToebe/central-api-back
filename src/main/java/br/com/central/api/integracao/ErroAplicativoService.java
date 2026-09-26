package br.com.central.api.integracao;

import br.com.central.api.comercial.Cliente;
import br.com.central.api.comercial.Contratacao;
import br.com.central.api.comercial.ContratacaoRepository;
import br.com.central.api.comercial.Produto;
import br.com.central.api.comercial.ProdutoRepository;
import br.com.central.api.integracao.IntegracaoDtos.ErroLinha;
import br.com.central.api.integracao.IntegracaoDtos.ErroRecebido;
import br.com.central.api.integracao.IntegracaoDtos.ErrosRecebidos;
import br.com.central.api.integracao.IntegracaoDtos.FiltroErros;
import br.com.central.api.web.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Tela "Logs" (26/09/2026): erros de servidor que os apps relatam. Guarda
 * 90 dias. A contratação é achada pelo {@code tenantId} (= {@code id_externo});
 * erro fora de uma paróquia (login, integração) fica sem contratação.
 */
@Service
public class ErroAplicativoService {

    static final Duration RETENCAO = Duration.ofDays(90);
    static final int MAX_LINHAS = 500;
    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final Logger log = LoggerFactory.getLogger(ErroAplicativoService.class);

    private final ProdutoRepository produtoRepository;
    private final ContratacaoRepository contratacaoRepository;
    private final ErroAplicativoRepository erroRepository;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public ErroAplicativoService(ProdutoRepository produtoRepository, ContratacaoRepository contratacaoRepository,
                                 ErroAplicativoRepository erroRepository, JdbcTemplate jdbc, Clock clock) {
        this.produtoRepository = produtoRepository;
        this.contratacaoRepository = contratacaoRepository;
        this.erroRepository = erroRepository;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Reenvio do mesmo lote não duplica: o id é do app ({@code ON CONFLICT}). */
    @Transactional
    public ErrosRecebidos receber(String produtoCodigo, List<ErroRecebido> erros) {
        Produto produto = produtoRepository.findByCodigoIgnoreCase(produtoCodigo)
                .orElseThrow(() -> new ResourceNotFoundException("Produto desconhecido."));
        Map<UUID, UUID> contratacaoPorTenant = new HashMap<>();
        int gravados = 0;
        for (ErroRecebido erro : erros) {
            UUID contratacaoId = erro.tenantId() == null ? null : contratacaoPorTenant.computeIfAbsent(erro.tenantId(),
                    tenant -> contratacaoRepository.findByProduto_IdAndIdExterno(produto.getId(), tenant)
                            .map(Contratacao::getId).orElse(null));
            gravados += jdbc.update("""
                    INSERT INTO public.erro_aplicativo (id, produto_id, contratacao_id, tenant_id, usuario_id, ocorrido_em,
                                                        metodo, rota, status, codigo, mensagem, request_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO NOTHING
                    """,
                    erro.id(), produto.getId(), contratacaoId, erro.tenantId(), erro.usuarioId(),
                    Timestamp.from(erro.ocorridoEm()), cortar(erro.metodo(), 10), cortar(erro.rota(), 300),
                    erro.status(), cortar(erro.codigo(), 60), cortar(erro.mensagem(), 1000), cortar(erro.requestId(), 100));
        }
        return new ErrosRecebidos(erros.size(), gravados);
    }

    @Transactional(readOnly = true)
    public List<ErroLinha> listar(FiltroErros filtro) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<ErroAplicativo> query = cb.createQuery(ErroAplicativo.class);
        Root<ErroAplicativo> erro = query.from(ErroAplicativo.class);
        @SuppressWarnings("unchecked")
        Join<ErroAplicativo, Produto> produto = (Join<ErroAplicativo, Produto>) erro.<ErroAplicativo, Produto>fetch("produto");
        @SuppressWarnings("unchecked")
        Join<ErroAplicativo, Contratacao> contratacao =
                (Join<ErroAplicativo, Contratacao>) erro.<ErroAplicativo, Contratacao>fetch("contratacao", JoinType.LEFT);
        @SuppressWarnings("unchecked")
        Join<Contratacao, Cliente> cliente =
                (Join<Contratacao, Cliente>) contratacao.<Contratacao, Cliente>fetch("cliente", JoinType.LEFT);

        List<Predicate> filtros = new ArrayList<>();
        if (filtro.produtoId() != null) {
            filtros.add(cb.equal(produto.get("id"), filtro.produtoId()));
        }
        if (filtro.contratacaoId() != null) {
            filtros.add(cb.equal(contratacao.get("id"), filtro.contratacaoId()));
        }
        if (filtro.de() != null) {
            filtros.add(cb.greaterThanOrEqualTo(erro.get("ocorridoEm"), filtro.de().atStartOfDay(BRASILIA).toInstant()));
        }
        if (filtro.ate() != null) {
            filtros.add(cb.lessThan(erro.get("ocorridoEm"), filtro.ate().plusDays(1).atStartOfDay(BRASILIA).toInstant()));
        }
        String busca = filtro.busca() == null || filtro.busca().isBlank() ? null : filtro.busca().trim();
        if (busca != null) {
            String padrao = "%" + busca.toLowerCase(Locale.ROOT) + "%";
            filtros.add(cb.or(
                    cb.like(cb.lower(erro.get("mensagem")), padrao),
                    cb.like(cb.lower(erro.get("rota")), padrao),
                    cb.like(cb.lower(erro.get("codigo")), padrao),
                    cb.like(cb.lower(erro.get("requestId")), padrao),
                    cb.like(cb.lower(cliente.get("nome")), padrao),
                    cb.like(cb.lower(contratacao.get("nomeInstancia")), padrao)));
        }
        query.select(erro).where(filtros.toArray(Predicate[]::new)).orderBy(cb.desc(erro.get("ocorridoEm")));
        return entityManager.createQuery(query).setMaxResults(MAX_LINHAS).getResultList().stream()
                .map(ErroAplicativoService::linha)
                .toList();
    }

    /** Todo dia às 04:30 (Brasília) apaga o que passou de 90 dias. */
    @Scheduled(cron = "0 30 4 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void apagarAntigos() {
        int apagados = erroRepository.apagarRecebidosAntesDe(clock.instant().minus(RETENCAO));
        if (apagados > 0) {
            log.info("Logs de erro: {} registro(s) com mais de 90 dias apagado(s).", apagados);
        }
    }

    private static ErroLinha linha(ErroAplicativo erro) {
        Contratacao contratacao = erro.getContratacao();
        return new ErroLinha(
                erro.getId(), erro.getOcorridoEm(), erro.getProduto().getCodigo(),
                contratacao == null ? null : contratacao.getId(),
                contratacao == null ? null : contratacao.getCliente().getNome(),
                contratacao == null ? null : contratacao.getNomeInstancia(),
                erro.getTenantId(), erro.getUsuarioId(), erro.getMetodo(), erro.getRota(), erro.getStatus(),
                erro.getCodigo(), erro.getMensagem(), erro.getRequestId());
    }

    private static String cortar(String texto, int limite) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= limite ? texto : texto.substring(0, limite);
    }
}
