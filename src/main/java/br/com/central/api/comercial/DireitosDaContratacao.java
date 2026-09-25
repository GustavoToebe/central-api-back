package br.com.central.api.comercial;

import br.com.central.api.comercial.dto.DireitosInstancia;
import br.com.central.api.security.OperadorAutenticado;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Recalcula o snapshot, sobe a versão e grava histórico e evento de saída
 * na mesma transação da alteração. O job que envia o evento fica para a
 * integração; aqui só se garante que ele existe ou não existe junto com a mudança.
 */
@Service
public class DireitosDaContratacao {

    static final String TIPO_EVENTO = "DIREITOS";

    private final PlanoRecursoRepository planoRecursoRepository;
    private final ContratacaoAdicionalRepository contratacaoAdicionalRepository;
    private final HistoricoContratacaoRepository historicoRepository;
    private final EventoSaidaRepository eventoRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public DireitosDaContratacao(PlanoRecursoRepository planoRecursoRepository,
                                 ContratacaoAdicionalRepository contratacaoAdicionalRepository,
                                 HistoricoContratacaoRepository historicoRepository,
                                 EventoSaidaRepository eventoRepository,
                                 JsonMapper jsonMapper,
                                 Clock clock) {
        this.planoRecursoRepository = planoRecursoRepository;
        this.contratacaoAdicionalRepository = contratacaoAdicionalRepository;
        this.historicoRepository = historicoRepository;
        this.eventoRepository = eventoRepository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional
    public DireitosInstancia publicar(Contratacao contratacao, String acao, String motivo) {
        contratacao.setVersaoDireitos(contratacao.getVersaoDireitos() + 1);
        return gravar(contratacao, acao, motivo);
    }

    /** Usado na criação, quando a versão 1 já foi reservada antes do insert. */
    @Transactional
    public DireitosInstancia gravarVersaoAtual(Contratacao contratacao, String acao, String motivo) {
        return gravar(contratacao, acao, motivo);
    }

    @Transactional(readOnly = true)
    public DireitosInstancia ler(Contratacao contratacao) {
        return jsonMapper.readValue(contratacao.getDireitosAtuais(), DireitosInstancia.class);
    }

    private DireitosInstancia gravar(Contratacao contratacao, String acao, String motivo) {
        DireitosInstancia snapshot = calcular(contratacao);
        String json = jsonMapper.writeValueAsString(snapshot);
        contratacao.setDireitosAtuais(json);
        historicoRepository.save(new HistoricoContratacao(
                contratacao.getId(), operadorAtualId(), acao, texto(motivo), contratacao.getVersaoDireitos()));
        for (EventoSaida pendente : eventoRepository.findByContratacaoIdAndSituacao(
                contratacao.getId(), SituacaoEvento.PENDENTE)) {
            pendente.descartar();
        }
        eventoRepository.save(new EventoSaida(
                contratacao.getId(),
                contratacao.getProduto().getCodigo(),
                TIPO_EVENTO,
                contratacao.getVersaoDireitos(),
                json,
                clock.instant()));
        return snapshot;
    }

    private DireitosInstancia calcular(Contratacao contratacao) {
        TreeMap<String, BigDecimal> limites = new TreeMap<>();
        List<String> funcionalidades = new ArrayList<>();
        for (PlanoRecurso item : planoRecursoRepository.listarDoPlano(contratacao.getPlano().getId())) {
            acumular(limites, funcionalidades, item.getRecurso(), item.getValor());
        }
        for (ContratacaoAdicional item : contratacaoAdicionalRepository.listarDaContratacao(contratacao.getId())) {
            BigDecimal total = item.getAdicional().getQuantidade().multiply(item.getQuantidade());
            acumular(limites, funcionalidades, item.getAdicional().getRecurso(), total);
        }
        funcionalidades.sort(Comparator.naturalOrder());
        SituacaoComercial situacao = contratacao.getSituacaoComercial();
        String motivo = situacao == SituacaoComercial.BLOQUEADA ? contratacao.getMotivoBloqueio() : null;
        return new DireitosInstancia(
                contratacao.getId(),
                contratacao.getCliente().getId(),
                contratacao.getProduto().getCodigo(),
                contratacao.getIdExterno(),
                contratacao.getVersaoDireitos(),
                situacao.name(),
                situacao.isAcessoLiberado(),
                motivo,
                contratacao.getVigenteAte(),
                new DireitosInstancia.PlanoResumo(contratacao.getPlano().getCodigo(), contratacao.getPlano().getNome()),
                limites,
                List.copyOf(funcionalidades),
                clock.instant());
    }

    private static void acumular(TreeMap<String, BigDecimal> limites, List<String> funcionalidades,
                                 Recurso recurso, BigDecimal valor) {
        if (recurso.getTipo() == TipoRecurso.LIMITE) {
            limites.merge(recurso.getCodigo(), valor, BigDecimal::add);
            return;
        }
        if (valor.signum() > 0 && !funcionalidades.contains(recurso.getCodigo())) {
            funcionalidades.add(recurso.getCodigo());
        }
    }

    private static UUID operadorAtualId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof OperadorAutenticado operador) {
            return operador.id();
        }
        return null;
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
