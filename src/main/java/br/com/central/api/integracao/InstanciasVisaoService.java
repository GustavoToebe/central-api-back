package br.com.central.api.integracao;

import br.com.central.api.comercial.Contratacao;
import br.com.central.api.comercial.SituacaoComercial;
import br.com.central.api.comercial.SituacaoProvisionamento;
import br.com.central.api.web.BadRequestException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Visão conjunta das instâncias (F06/F26). Lê o último resumo de consumo já guardado (histórico diário), sem HTTP
 * para os aplicativos: ausência de resumo é SEM_DADOS, nunca consumo zero. A varredura é uma ação manual do operador,
 * limitada a poucas instâncias por vez, com o HTTP fora de transação e uma por vez.
 */
@Service
public class InstanciasVisaoService {
    static final int LIMITE_INSTANCIAS = 500;
    static final int POR_PAGINA = 30;
    static final int MAXIMO_VARREDURA = 5;
    static final Duration DEFASAGEM = Duration.ofHours(48);
    private static final Duration VALIDADE_RESUMO_METRICAS = Duration.ofMinutes(5);

    public enum Nivel { CRITICO, ATENCAO, OK, SEM_DADOS }

    public record Alerta(String codigo, String nome, String estado, Long usado, Long limite) {}

    public record Instancia(UUID contratacaoId, Long sequencial, String cliente, String instancia, String plano,
                            SituacaoComercial situacaoComercial, Nivel nivel, boolean defasado, Instant consultadoEm,
                            List<Alerta> alertas) {}

    public record Pagina(List<Instancia> itens, long total, int pagina, int tamanho, Map<Nivel, Long> porNivel, long defasadas) {}

    public record ResultadoVarredura(int consultadas, int falhas, int restantesSemDadosOuDefasadas) {}

    @PersistenceContext
    private EntityManager em;
    private final JsonMapper json;
    private final ConsumoCentralService consumo;
    private final AtomicBoolean emVarredura = new AtomicBoolean();
    private volatile Pagina resumoMetricas;
    private volatile Instant resumoMetricasEm = Instant.EPOCH;

    public InstanciasVisaoService(JsonMapper json, ConsumoCentralService consumo) {
        this.json = json;
        this.consumo = consumo;
    }

    @Transactional(readOnly = true)
    public Pagina visao(Nivel filtro, int pagina) {
        if (pagina < 0 || pagina > 100000) {
            throw new BadRequestException("Página inválida.");
        }
        List<Instancia> todas = montar();
        Map<Nivel, Long> porNivel = new EnumMap<>(Nivel.class);
        for (Nivel n : Nivel.values()) {
            porNivel.put(n, 0L);
        }
        todas.forEach(i -> porNivel.merge(i.nivel(), 1L, Long::sum));
        long defasadas = todas.stream().filter(Instancia::defasado).count();
        List<Instancia> filtradas = todas.stream().filter(i -> filtro == null || i.nivel() == filtro)
                .sorted(Comparator.comparing((Instancia i) -> i.nivel().ordinal()).thenComparing(i -> i.cliente().toLowerCase(Locale.ROOT))
                        .thenComparing(Instancia::contratacaoId)).toList();
        int de = Math.min(filtradas.size(), pagina * POR_PAGINA);
        int ate = Math.min(filtradas.size(), de + POR_PAGINA);
        return new Pagina(filtradas.subList(de, ate), filtradas.size(), pagina, POR_PAGINA, porNivel, defasadas);
    }

    /** Cópia curta em memória para a exportação de métricas; evita reler centenas de resumos a cada coleta. */
    public Pagina resumoParaMetricas() {
        Instant agora = Instant.now();
        Pagina atual = resumoMetricas;
        if (atual == null || resumoMetricasEm.plus(VALIDADE_RESUMO_METRICAS).isBefore(agora)) {
            atual = visao(null, 0);
            resumoMetricas = atual;
            resumoMetricasEm = agora;
        }
        return atual;
    }

    /** Consulta, em série, as instâncias sem resumo ou com o resumo mais antigo. HTTP fora de transação. */
    public ResultadoVarredura varrer() {
        if (!emVarredura.compareAndSet(false, true)) {
            throw new BadRequestException("Já existe uma atualização em andamento. Aguarde.");
        }
        try {
            List<UUID> fila = candidatas();
            int consultadas = 0;
            int falhas = 0;
            for (UUID id : fila.stream().limit(MAXIMO_VARREDURA).toList()) {
                try {
                    consumo.consultar(id);
                    consultadas++;
                } catch (RuntimeException e) {
                    falhas++;
                }
            }
            resumoMetricasEm = Instant.EPOCH;
            return new ResultadoVarredura(consultadas, falhas, Math.max(0, fila.size() - MAXIMO_VARREDURA));
        } finally {
            emVarredura.set(false);
        }
    }

    List<UUID> candidatas() {
        List<Instancia> todas = montar();
        Instant agora = Instant.now();
        return todas.stream()
                .filter(i -> i.consultadoEm() == null || i.defasado() || i.consultadoEm().isBefore(agora.minus(Duration.ofHours(12))))
                .sorted(Comparator.comparing((Instancia i) -> i.consultadoEm() == null ? Instant.EPOCH : i.consultadoEm()))
                .map(Instancia::contratacaoId).toList();
    }

    private List<Instancia> montar() {
        List<Contratacao> contratacoes = em.createQuery("select c from Contratacao c join fetch c.cliente join fetch c.produto join fetch c.plano "
                        + "where c.situacaoProvisionamento=:ativa and c.situacaoComercial<>:cancelada order by c.sequencial", Contratacao.class)
                .setParameter("ativa", SituacaoProvisionamento.ATIVA).setParameter("cancelada", SituacaoComercial.CANCELADA)
                .setMaxResults(LIMITE_INSTANCIAS).getResultList();
        if (contratacoes.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = contratacoes.stream().map(Contratacao::getId).toList();
        Map<UUID, ConsumoHistorico> ultimos = em.createQuery("select h from ConsumoHistorico h where h.escopo in :ids and h.dia=(select max(h2.dia) from ConsumoHistorico h2 where h2.escopo=h.escopo)",
                ConsumoHistorico.class).setParameter("ids", ids).getResultList().stream()
                .collect(Collectors.toMap(h -> h.escopo, h -> h, (a, b) -> a));
        Instant agora = Instant.now();
        List<Instancia> lista = new ArrayList<>();
        for (Contratacao c : contratacoes) {
            ConsumoHistorico h = ultimos.get(c.getId());
            List<Alerta> alertas = List.of();
            Nivel nivel = Nivel.SEM_DADOS;
            boolean defasado = true;
            if (h != null) {
                defasado = h.consultadoEm.isBefore(agora.minus(DEFASAGEM));
                try {
                    alertas = alertas(json.readValue(h.dados, ConsumoCentralService.Consumo.class));
                    nivel = nivel(alertas);
                } catch (RuntimeException e) {
                    nivel = Nivel.SEM_DADOS;
                }
            }
            lista.add(new Instancia(c.getId(), c.getSequencial(), c.getCliente().getNome(), c.getNomeInstancia(), c.getPlano().getNome(),
                    c.getSituacaoComercial(), nivel, defasado, h == null ? null : h.consultadoEm, alertas));
        }
        return lista;
    }

    static List<Alerta> alertas(ConsumoCentralService.Consumo consumo) {
        if (consumo == null || consumo.itens() == null) {
            return List.of();
        }
        return consumo.itens().stream()
                .filter(i -> i.estado() != null && !"DISPONIVEL".equals(i.estado()))
                .map(i -> new Alerta(i.codigo(), i.nome(), i.estado(), i.usado(), i.limite())).toList();
    }

    static Nivel nivel(List<Alerta> alertas) {
        if (alertas.stream().anyMatch(a -> "EXCEDIDO".equals(a.estado()) || "ATINGIDO".equals(a.estado()))) {
            return Nivel.CRITICO;
        }
        if (alertas.stream().anyMatch(a -> "ATENCAO".equals(a.estado()) || "INVENTARIO_PENDENTE".equals(a.estado()))) {
            return Nivel.ATENCAO;
        }
        return Nivel.OK;
    }
}
