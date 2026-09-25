package br.com.central.api.integracao;

import br.com.central.api.comercial.Contratacao;
import br.com.central.api.comercial.ContratacaoRepository;
import br.com.central.api.comercial.dto.DireitosInstancia;
import br.com.central.api.web.BadRequestException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.List;

/** Página de direitos das contratações que já têm instância no aplicativo. */
@Service
public class ConsultaDireitos {

    private final ContratacaoRepository contratacaoRepository;
    private final JsonMapper json;
    private final Clock clock;

    public ConsultaDireitos(ContratacaoRepository contratacaoRepository, JsonMapper json, Clock clock) {
        this.contratacaoRepository = contratacaoRepository;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public IntegracaoDtos.PaginaDireitos listar(String produto, int pagina, int tamanho) {
        if (pagina < 0 || tamanho < 1 || tamanho > 100) {
            throw new BadRequestException("Página a partir de 0 e tamanho entre 1 e 100.");
        }
        Page<Contratacao> page = contratacaoRepository.provisionadasDoProduto(
                produto, PageRequest.of(pagina, tamanho, Sort.by("criadoEm")));
        List<DireitosInstancia> itens = page.getContent().stream().map(this::comTenant).toList();
        return new IntegracaoDtos.PaginaDireitos(itens, pagina, tamanho, page.getTotalElements(), clock.instant());
    }

    private DireitosInstancia comTenant(Contratacao contratacao) {
        DireitosInstancia atual = json.readValue(contratacao.getDireitosAtuais(), DireitosInstancia.class);
        if (atual.tenantId() != null) {
            return atual;
        }
        return new DireitosInstancia(
                atual.contratacaoId(), atual.clienteId(), atual.produto(), contratacao.getIdExterno(),
                atual.versao(), atual.situacao(), atual.acessoLiberado(), atual.motivoBloqueio(),
                atual.vigenteAte(), atual.plano(), atual.limites(), atual.funcionalidades(), atual.geradoEm());
    }
}
