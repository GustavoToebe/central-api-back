package br.com.central.api.integracao;

import br.com.central.api.comercial.Contratacao;
import br.com.central.api.comercial.ContratacaoRepository;
import br.com.central.api.comercial.ContratacaoService;
import br.com.central.api.comercial.EventoSaida;
import br.com.central.api.comercial.EventoSaidaRepository;
import br.com.central.api.comercial.HistoricoContratacao;
import br.com.central.api.comercial.HistoricoContratacaoRepository;
import br.com.central.api.comercial.SituacaoComercial;
import br.com.central.api.comercial.SituacaoEvento;
import br.com.central.api.comercial.SituacaoProvisionamento;
import br.com.central.api.comercial.dto.ContratacaoDtos.ContratacaoResponse;
import br.com.central.api.comercial.dto.DireitosInstancia;
import br.com.central.api.web.BadRequestException;
import br.com.central.api.web.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Entrega o outbox. Sem {@code id_externo} o evento vira POST de
 * provisionamento; com {@code id_externo} vira PUT de direitos. 409 e 422
 * não repetem. Rede, timeout e os demais status seguem a agenda. A
 * Idempotency-Key é o id da contratação em toda tentativa — o contrato e o
 * Servirea exigem que ela seja igual ao {@code contratacaoId} do corpo. Cada
 * resposta do POST fica em {@code ultimo_status_provisionamento} (0 = sem
 * resposta), que decide se o operador ainda pode editar nome, slug e
 * administrador ({@link Contratacao#dadosDeProvisionamentoEditaveis()}).
 */
@Service
public class EntregaIntegracao {

    private static final Logger log = LoggerFactory.getLogger(EntregaIntegracao.class);
    private static final String CAMINHO_PROVISIONAR = "/integracao/v1/instancias";
    private static final String TIPO_DIREITOS = "DIREITOS";

    private final EventoSaidaRepository eventoRepository;
    private final ContratacaoRepository contratacaoRepository;
    private final HistoricoContratacaoRepository historicoRepository;
    private final ContratacaoService contratacaoService;
    private final AplicativoHttp http;
    private final JsonMapper json;
    private final Clock clock;
    private final TransactionTemplate transacao;

    public EntregaIntegracao(EventoSaidaRepository eventoRepository,
                             ContratacaoRepository contratacaoRepository,
                             HistoricoContratacaoRepository historicoRepository,
                             ContratacaoService contratacaoService,
                             AplicativoHttp http,
                             JsonMapper json,
                             Clock clock,
                             PlatformTransactionManager transactionManager) {
        this.eventoRepository = eventoRepository;
        this.contratacaoRepository = contratacaoRepository;
        this.historicoRepository = historicoRepository;
        this.contratacaoService = contratacaoService;
        this.http = http;
        this.json = json;
        this.clock = clock;
        this.transacao = new TransactionTemplate(transactionManager);
    }

    public int enviarProntos() {
        List<UUID> ids = transacao.execute(status ->
                eventoRepository.idsProntos(SituacaoEvento.PENDENTE, clock.instant(), PageRequest.of(0, 30)));
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int tratados = 0;
        for (UUID id : ids) {
            try {
                if (entregar(id)) tratados++;
            } catch (RuntimeException e) {
                log.error("Falha ao entregar o evento {}.", id, e);
            }
        }
        return tratados;
    }

    public ContratacaoResponse tentarNovamente(UUID contratacaoId) {
        UUID eventoId = transacao.execute(status -> prepararNovaTentativa(contratacaoId));
        if (eventoId != null) {
            entregar(eventoId);
        }
        return contratacaoService.buscar(contratacaoId);
    }

    private UUID prepararNovaTentativa(UUID contratacaoId) {
        Contratacao contratacao = carregarParaAlterar(contratacaoId);
        if (contratacao.getSituacaoComercial() == SituacaoComercial.CANCELADA) {
            throw new BadRequestException("Contratação cancelada não é provisionada de novo.");
        }
        if (contratacao.getIdExterno() != null) {
            throw new BadRequestException("A instância já foi provisionada.");
        }
        contratacao.setSituacaoProvisionamento(SituacaoProvisionamento.PENDENTE);
        List<EventoSaida> pendentes = eventoRepository.findByContratacaoIdAndSituacao(
                contratacaoId, SituacaoEvento.PENDENTE);
        if (pendentes.isEmpty()) {
            EventoSaida novo = eventoRepository.save(new EventoSaida(
                    contratacao.getId(),
                    contratacao.getProduto().getCodigo(),
                    TIPO_DIREITOS,
                    contratacao.getVersaoDireitos(),
                    contratacao.getDireitosAtuais(),
                    clock.instant()));
            return novo.getId();
        }
        pendentes.forEach(evento -> evento.adiantar(clock.instant()));
        return pendentes.getFirst().getId();
    }

    /** Reserva e conclusão usam transações curtas; o HTTP nunca ocupa a conexão do banco. */
    private boolean entregar(UUID eventoId) {
        Envio envio = transacao.execute(status -> reservar(eventoId));
        if (envio == null) return false;
        AplicativoHttp.Resposta resposta = null;
        String falha = null;
        try {
            resposta = http.enviar(envio.url(), envio.metodo(), envio.caminho(), envio.corpo(), envio.idempotencia());
        } catch (RuntimeException e) {
            falha = e.getClass().getSimpleName();
        }
        AplicativoHttp.Resposta resultado = resposta;
        String erro = falha;
        transacao.executeWithoutResult(status -> concluir(envio, resultado, erro));
        return true;
    }

    private Envio reservar(UUID eventoId) {
        UUID contratacaoId = eventoRepository.contratacaoDoEvento(eventoId).orElse(null);
        if (contratacaoId == null) return null;
        // Ordem única de trava: contratação, depois evento. Serializa também versões distintas.
        Contratacao contratacao = carregarParaAlterar(contratacaoId);
        EventoSaida evento = eventoRepository.buscarParaAlterar(eventoId).orElse(null);
        if (evento == null || evento.getSituacao() != SituacaoEvento.PENDENTE
                || evento.getProximaTentativa().isAfter(clock.instant()) || evento.reservaAtiva(clock.instant())
                || eventoRepository.existsByContratacaoIdAndReservaAteAfter(contratacao.getId(), clock.instant())) return null;
        boolean provisionar = contratacao.getIdExterno() == null;
        if (provisionar && contratacao.getSituacaoComercial() == SituacaoComercial.CANCELADA) {
            descartarProvisionamento(contratacao, evento);
            return null;
        }
        UUID dono = UUID.randomUUID();
        String caminho = provisionar ? CAMINHO_PROVISIONAR
                : CAMINHO_PROVISIONAR + "/" + contratacao.getIdExterno() + "/direitos";
        byte[] corpo = provisionar ? corpoProvisionamento(contratacao, evento)
                : json.writeValueAsBytes(comTenant(json.readValue(evento.getPayload(), DireitosInstancia.class), contratacao.getIdExterno()));
        evento.reservar(dono, clock.instant().plusSeconds(120));
        if (provisionar) {
            contratacao.setSituacaoProvisionamento(SituacaoProvisionamento.PROCESSANDO);
            // Mesmo se houver crash depois do POST, não liberar edição de identidade com a mesma chave.
            contratacao.setUltimoStatusProvisionamento(0);
        }
        return new Envio(evento.getId(), contratacao.getId(), dono, provisionar,
                contratacao.getProduto().getUrlBaseIntegracao(), provisionar ? "POST" : "PUT", caminho,
                corpo, provisionar ? contratacao.getId().toString() : null);
    }

    private void concluir(Envio envio, AplicativoHttp.Resposta resposta, String erro) {
        Contratacao contratacao = carregarParaAlterar(envio.contratacaoId());
        EventoSaida evento = eventoRepository.buscarParaAlterar(envio.eventoId()).orElse(null);
        if (evento == null || !evento.pertenceA(envio.dono())) return;
        // Outra tentativa pode assumir após expiração. A resposta antiga nunca deve liberar a nova reserva.
        if (!evento.reservaAtiva(clock.instant())) return;
        boolean obsoleto = evento.getSituacao() != SituacaoEvento.PENDENTE;
        evento.liberarReserva();
        if (envio.provisionar() && resposta != null && resposta.status() >= 200 && resposta.status() < 300) {
            // Os direitos podem mudar durante o HTTP. Preservar a versão atual e gravar a identidade criada.
            contratacao.setUltimoStatusProvisionamento(resposta.status());
            if (!concluirProvisionamento(contratacao, evento, resposta)) return;
            if (!obsoleto) evento.marcarEnviado();
            return;
        }
        if (obsoleto) return;
        if (envio.provisionar()) contratacao.setUltimoStatusProvisionamento(resposta == null ? 0 : resposta.status());
        if (resposta == null) {
            repetir(contratacao, evento, envio.provisionar(), erro);
        } else if (resposta.status() >= 200 && resposta.status() < 300) {
            evento.marcarEnviado();
        } else if (resposta.status() == 409 || resposta.status() == 422) {
            falhaDefinitiva(contratacao, evento, envio.provisionar(), "HTTP " + resposta.status());
        } else {
            repetir(contratacao, evento, envio.provisionar(), "HTTP " + resposta.status());
        }
    }

    private record Envio(UUID eventoId, UUID contratacaoId, UUID dono, boolean provisionar,
                         String url, String metodo, String caminho, byte[] corpo, String idempotencia) { }

    private byte[] corpoProvisionamento(Contratacao contratacao, EventoSaida evento) {
        DireitosInstancia direitos = json.readValue(evento.getPayload(), DireitosInstancia.class);
        CorpoProvisionamento corpo = new CorpoProvisionamento(
                contratacao.getId(),
                contratacao.getCliente().getId(),
                new CorpoProvisionamento.Instancia(contratacao.getNomeInstancia(), contratacao.getSlugInstancia()),
                new CorpoProvisionamento.Administrador(contratacao.getAdminNome(), contratacao.getAdminEmail()),
                direitos);
        return json.writeValueAsBytes(corpo);
    }

    /** @return false quando a resposta 2xx não trouxe tenant e a entrega já foi encerrada */
    private boolean concluirProvisionamento(Contratacao contratacao, EventoSaida evento, AplicativoHttp.Resposta resposta) {
        JsonNode no;
        try {
            no = json.readTree(resposta.corpo());
        } catch (RuntimeException e) {
            falhaDefinitiva(contratacao, evento, true, "Resposta de provisionamento inválida.");
            return false;
        }
        String texto = no == null ? null : no.path("tenantId").asString(null);
        if (texto == null || texto.isBlank()) {
            falhaDefinitiva(contratacao, evento, true, "Resposta sem tenantId.");
            return false;
        }
        UUID tenantId;
        try {
            tenantId = UUID.fromString(texto);
        } catch (IllegalArgumentException e) {
            falhaDefinitiva(contratacao, evento, true, "tenantId inválido.");
            return false;
        }
        DireitosInstancia atual = json.readValue(contratacao.getDireitosAtuais(), DireitosInstancia.class);
        contratacao.setDireitosAtuais(json.writeValueAsString(comTenant(atual, tenantId)));
        contratacao.setIdExterno(tenantId);
        contratacao.setSituacaoProvisionamento(SituacaoProvisionamento.ATIVA);
        historicoRepository.save(new HistoricoContratacao(
                contratacao.getId(), null, "PROVISIONADA", null, contratacao.getVersaoDireitos()));
        return true;
    }

    /**
     * Cancelada antes de chegar ao app: não cria a instância. Achado no teste
     * local de 26/09/2026: a contratação foi cancelada enquanto o app estava
     * fora do ar, e a repetição seguinte criou a paróquia (já bloqueada) e
     * mandou o convite ao administrador. O botão "Tentar novamente" já
     * recusava cancelada; a repetição automática passa a recusar também.
     * Se um envio anterior criou a instância sem a Central saber (timeout),
     * ela fica sem confirmação de direitos e o app a bloqueia pela regra das 72 h.
     */
    private void descartarProvisionamento(Contratacao contratacao, EventoSaida evento) {
        evento.descartar();
        historicoRepository.save(new HistoricoContratacao(
                contratacao.getId(), null, "PROVISIONAMENTO_DESCARTADO",
                "Contratação cancelada antes de chegar ao aplicativo.", contratacao.getVersaoDireitos()));
        log.info("Provisionamento da contratação {} descartado: cancelada antes do envio.", contratacao.getId());
    }

    private void repetir(Contratacao contratacao, EventoSaida evento, boolean provisionar, String motivo) {
        Instant criadoEm = evento.getCriadoEm() == null ? clock.instant() : evento.getCriadoEm();
        AgendaDeEntrega.Decisao decisao = AgendaDeEntrega.decidir(
                provisionar ? AgendaDeEntrega.Tipo.PROVISIONAMENTO : AgendaDeEntrega.Tipo.WEBHOOK,
                evento.getTentativas(),
                criadoEm,
                clock.instant());
        if (decisao.parar()) {
            falhaDefinitiva(contratacao, evento, provisionar, motivo);
            return;
        }
        evento.reagendar(decisao.quando());
        log.warn("Entrega da contratação {} adiada para {} ({}).", contratacao.getId(), decisao.quando(), motivo);
    }

    private void falhaDefinitiva(Contratacao contratacao, EventoSaida evento, boolean provisionar, String motivo) {
        evento.marcarFalhou();
        String texto = motivo == null ? "falha" : motivo;
        if (texto.length() > 300) {
            texto = texto.substring(0, 300);
        }
        if (provisionar) {
            contratacao.setSituacaoProvisionamento(SituacaoProvisionamento.ERRO);
            historicoRepository.save(new HistoricoContratacao(
                    contratacao.getId(), null, "PROVISIONAMENTO_ERRO", texto, contratacao.getVersaoDireitos()));
        }
        log.error("Alerta: entrega do evento {} da contratação {} esgotou as tentativas. {}",
                evento.getId(), contratacao.getId(), texto);
    }

    private Contratacao carregarParaAlterar(UUID id) {
        contratacaoRepository.buscarParaAlterar(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contratação não encontrada."));
        return carregar(id);
    }

    private Contratacao carregar(UUID id) {
        Contratacao contratacao = contratacaoRepository.buscarComReferencias(id);
        if (contratacao == null) {
            throw new ResourceNotFoundException("Contratação não encontrada.");
        }
        return contratacao;
    }

    private static DireitosInstancia comTenant(DireitosInstancia atual, UUID tenantId) {
        return new DireitosInstancia(atual.contratacaoId(), atual.clienteId(), atual.produto(), tenantId,
                atual.versao(), atual.situacao(), atual.acessoLiberado(), atual.motivoBloqueio(),
                atual.vigenteAte(), atual.plano(), atual.limites(), atual.funcionalidades(), atual.geradoEm());
    }

    private record CorpoProvisionamento(
            UUID contratacaoId,
            UUID clienteId,
            Instancia instancia,
            Administrador administrador,
            DireitosInstancia direitos
    ) {
        private record Instancia(String nome, String slug) {
        }

        private record Administrador(String nome, String email) {
        }
    }
}
