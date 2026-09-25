package br.com.central.api.integracao;

import br.com.central.api.comercial.Contratacao;
import br.com.central.api.comercial.ContratacaoRepository;
import br.com.central.api.operador.Operador;
import br.com.central.api.operador.OperadorAuditoria;
import br.com.central.api.operador.OperadorRepository;
import br.com.central.api.security.OperadorAutenticado;
import br.com.central.api.web.BadRequestException;
import br.com.central.api.web.ResourceNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Pede ao aplicativo um código de suporte e devolve a URL ao painel. */
@Service
public class SuporteCentralService {

    private final ContratacaoRepository contratacaoRepository;
    private final OperadorRepository operadorRepository;
    private final OperadorAuditoria auditoria;
    private final AplicativoHttp http;
    private final JsonMapper json;

    public SuporteCentralService(ContratacaoRepository contratacaoRepository,
                                 OperadorRepository operadorRepository,
                                 OperadorAuditoria auditoria,
                                 AplicativoHttp http,
                                 JsonMapper json) {
        this.contratacaoRepository = contratacaoRepository;
        this.operadorRepository = operadorRepository;
        this.auditoria = auditoria;
        this.http = http;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public IntegracaoDtos.SuporteResponse pedir(UUID contratacaoId, String motivo, String ip) {
        Contratacao contratacao = contratacaoRepository.buscarComReferencias(contratacaoId);
        if (contratacao == null) {
            throw new ResourceNotFoundException("Contratação não encontrada.");
        }
        if (contratacao.getIdExterno() == null) {
            throw new BadRequestException("A contratação ainda não foi provisionada.");
        }
        OperadorAutenticado autenticado = operadorAtual();
        Operador operador = operadorRepository.findById(autenticado.id())
                .orElseThrow(() -> new ResourceNotFoundException("Operador não encontrado."));
        byte[] corpo = json.writeValueAsBytes(Map.of(
                "operador", Map.of(
                        "id", operador.getId(),
                        "nome", operador.getNome(),
                        "email", operador.getEmail()),
                "motivo", motivo.trim()));
        String caminho = "/integracao/v1/instancias/" + contratacao.getIdExterno() + "/suporte";
        AplicativoHttp.Resposta resposta;
        try {
            resposta = http.enviar(contratacao.getProduto().getUrlBaseIntegracao(), "POST", caminho, corpo, null);
        } catch (RuntimeException e) {
            throw new BadRequestException("Não foi possível falar com o aplicativo.");
        }
        if (resposta.status() != 200 && resposta.status() != 201) {
            throw new BadRequestException("O aplicativo recusou o pedido de suporte.");
        }
        JsonNode no = json.readTree(resposta.corpo());
        auditoria.registrar(operador.getId(), "SUPORTE",
                "contratação " + contratacao.getId() + " motivo " + motivo.trim(), ip);
        return new IntegracaoDtos.SuporteResponse(
                no.path("codigo").asString(),
                no.path("urlAcesso").asString(),
                Instant.parse(no.path("expiraEm").asString()));
    }

    private static OperadorAutenticado operadorAtual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof OperadorAutenticado operador) {
            return operador;
        }
        throw new BadRequestException("Operador não identificado.");
    }
}
