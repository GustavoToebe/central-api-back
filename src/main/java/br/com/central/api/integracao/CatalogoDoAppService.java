package br.com.central.api.integracao;

import br.com.central.api.comercial.CatalogoService;
import br.com.central.api.comercial.Produto;
import br.com.central.api.comercial.RecursoRepository;
import br.com.central.api.comercial.TipoRecurso;
import br.com.central.api.integracao.IntegracaoDtos.RecursoDoAppResponse;
import br.com.central.api.web.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pergunta ao app quais limites e funcionalidades ele entende (contrato 5.5),
 * para a tela de Recursos sugerir o código exato (26/09/2026). Antes o
 * operador digitava e o painel punha em maiúsculas: "VOLUNTARIOS" nunca
 * casaria com o "voluntarios" que o app lê.
 */
@Service
public class CatalogoDoAppService {

    static final String CAMINHO = "/integracao/v1/recursos";

    private final CatalogoService catalogoService;
    private final RecursoRepository recursoRepository;
    private final AplicativoHttp http;
    private final JsonMapper json;

    public CatalogoDoAppService(CatalogoService catalogoService, RecursoRepository recursoRepository,
                                AplicativoHttp http, JsonMapper json) {
        this.catalogoService = catalogoService;
        this.recursoRepository = recursoRepository;
        this.http = http;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<RecursoDoAppResponse> sugestoes(UUID produtoId) {
        Produto produto = catalogoService.carregarProduto(produtoId);
        if (produto.getUrlBaseIntegracao() == null || produto.getUrlBaseIntegracao().isBlank()) {
            throw new BadRequestException("Produto sem URL de integração: não há como perguntar ao aplicativo.");
        }
        AplicativoHttp.Resposta resposta;
        try {
            resposta = http.enviar(produto.getUrlBaseIntegracao(), "GET", CAMINHO, null, null);
        } catch (RuntimeException e) {
            throw new BadRequestException("Não foi possível falar com o aplicativo.");
        }
        if (resposta.status() != 200) {
            throw new BadRequestException("O aplicativo não respondeu a lista de recursos (HTTP " + resposta.status() + ").");
        }
        List<RecursoDoAppResponse> itens = new ArrayList<>();
        for (JsonNode no : json.readTree(resposta.corpo())) {
            String codigo = no.path("codigo").asString();
            TipoRecurso tipo = tipo(no.path("tipo").asString());
            if (codigo.isBlank() || tipo == null) {
                continue;
            }
            itens.add(new RecursoDoAppResponse(
                    codigo,
                    no.path("nome").asString(codigo),
                    tipo,
                    no.path("unidade").isNull() ? null : no.path("unidade").asString(null),
                    no.path("aplicado").asBoolean(false),
                    recursoRepository.existsByProduto_IdAndCodigo(produtoId, codigo)));
        }
        return itens;
    }

    /** Tipo que a Central não conhece é ignorado, não derruba a lista. */
    private static TipoRecurso tipo(String valor) {
        try {
            return TipoRecurso.valueOf(valor);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
