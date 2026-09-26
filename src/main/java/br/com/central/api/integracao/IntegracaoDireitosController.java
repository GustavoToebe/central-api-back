package br.com.central.api.integracao;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/integracao/v1")
public class IntegracaoDireitosController {

    private final ConsultaDireitos consulta;
    private final ErroAplicativoService erros;

    public IntegracaoDireitosController(ConsultaDireitos consulta, ErroAplicativoService erros) {
        this.consulta = consulta;
        this.erros = erros;
    }

    /** Contrato 6.2: erros de servidor do app para a tela "Logs". */
    @PostMapping("/produtos/{produto}/erros")
    @PreAuthorize("hasAuthority('PERM_INTEGRACAO')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public IntegracaoDtos.ErrosRecebidos erros(@PathVariable String produto,
                                               @Valid @RequestBody IntegracaoDtos.LoteDeErros lote) {
        return erros.receber(produto, lote.erros());
    }

    @GetMapping("/produtos/{produto}/direitos")
    @PreAuthorize("hasAuthority('PERM_INTEGRACAO')")
    public IntegracaoDtos.PaginaDireitos direitos(@PathVariable String produto,
                                                  @RequestParam(defaultValue = "0") int pagina,
                                                  @RequestParam(defaultValue = "100") int tamanho) {
        return consulta.listar(produto, pagina, tamanho);
    }
}
