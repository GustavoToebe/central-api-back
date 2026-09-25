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
 * Servire exigem que ela seja igual ao {@code contratacaoId} do corpo. Cada
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
                eventoRepository.idsProntos(SituacaoEvento.PENDENTE, clock.instant()));
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int tratados = 0;
        for (UUID id : ids) {
            try {
                transacao.executeWithoutResult(status -> entregar(id));
                tratados++;
            } catch (RuntimeException e) {
                log.error("Falha ao entregar o evento {}.", id, e);
            }
        }
        return tratados;
    }

    public ContratacaoResponse tentarNovamente(UUID contratacaoId) {
        UUID eventoId = transacao.execute(status -> prepararNovaTentativa(contratacaoId));
        if (eventoId != null) {
            transacao.executeWithoutResult(status -> entregar(eventoId));
        }
        return contratacaoService.buscar(contratacaoId);
    }

    private UUID prepararNovaTentativa(UUID contratacaoId) {
        Contratacao contratacao = carregar(contratacaoId);
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

    private void entregar(UUID eventoId) {
        EventoSaida evento = eventoRepository.findById(eventoId).orElse(null);
        if (evento == null || evento.getSituacao() != SituacaoEvento.PENDENTE) {
            return;
        }
        if (evento.getProximaTentativa().isAfter(clock.instant())) {
            return;
        }
        Contratacao contratacao = carregar(evento.getContratacaoId());
        boolean provisionar = contratacao.getIdExterno() == null;
        if (provisionar && contratacao.getSituacaoProvisionamento() == SituacaoProvisionamento.PENDENTE) {
            contratacao.setSituacaoProvisionamento(SituacaoProvisionamento.PROCESSANDO);
        }
        try {
            AplicativoHttp.Resposta resposta = chamar(contratacao, evento, provisionar);
            if (provisionar) {
                contratacao.setUltimoStatusProvisionamento(resposta.status());
            }
            if (resposta.status() >= 200 && resposta.status() < 300) {
                if (provisionar && !concluirProvisionamento(contratacao, evento, resposta)) {
                    return;
                }
                evento.marcarEnviado();
                return;
            }
            if (resposta.status() == 409 || resposta.status() == 422) {
                falhaDefinitiva(contratacao, evento, provisionar, "HTTP " + resposta.status() + " " + trecho(resposta.corpo()));
                return;
            }
            repetir(contratacao, evento, provisionar, "HTTP " + resposta.status());
        } catch (RuntimeException e) {
            if (provisionar) {
                contratacao.setUltimoStatusProvisionamento(0);
            }
            repetir(contratacao, evento, provisionar, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private AplicativoHttp.Resposta chamar(Contratacao contratacao, EventoSaida evento, boolean provisionar) {
        if (provisionar) {
            return http.enviar(
                    contratacao.getProduto().getUrlBaseIntegracao(),
                    "POST",
                    CAMINHO_PROVISIONAR,
                    corpoProvisionamento(contratacao, evento),
                    contratacao.getId().toString());
        }
        String caminho = CAMINHO_PROVISIONAR + "/" + contratacao.getIdExterno() + "/direitos";
        return http.enviar(
                contratacao.getProduto().getUrlBaseIntegracao(),
                "PUT",
                caminho,
                evento.getPayload().getBytes(StandardCharsets.UTF_8),
                null);
    }

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
        JsonNode no = json.readTree(resposta.corpo());
        String texto = no.path("tenantId").asString(null);
        if (texto == null || texto.isBlank()) {
            falhaDefinitiva(contratacao, evento, true, "Resposta sem tenantId.");
            return false;
        }
        UUID tenantId = UUID.fromString(texto);
        DireitosInstancia atual = json.readValue(contratacao.getDireitosAtuais(), DireitosInstancia.class);
        DireitosInstancia comTenant = new DireitosInstancia(
                atual.contratacaoId(), atual.clienteId(), atual.produto(), tenantId,
                atual.versao(), atual.situacao(), atual.acessoLiberado(), atual.motivoBloqueio(),
                atual.vigenteAte(), atual.plano(), atual.limites(), atual.funcionalidades(), atual.geradoEm());
        contratacao.setDireitosAtuais(json.writeValueAsString(comTenant));
        contratacao.setIdExterno(tenantId);
        contratacao.setSituacaoProvisionamento(SituacaoProvisionamento.ATIVA);
        historicoRepository.save(new HistoricoContratacao(
                contratacao.getId(), null, "PROVISIONADA", null, contratacao.getVersaoDireitos()));
        return true;
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

    private Contratacao carregar(UUID id) {
        Contratacao contratacao = contratacaoRepository.buscarComReferencias(id);
        if (contratacao == null) {
            throw new ResourceNotFoundException("Contratação não encontrada.");
        }
        return contratacao;
    }

    private static String trecho(byte[] corpo) {
        if (corpo == null || corpo.length == 0) {
            return "";
        }
        String texto = new String(corpo, StandardCharsets.UTF_8).replaceAll("\\s+", " ");
        return texto.length() > 180 ? texto.substring(0, 180) : texto;
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
