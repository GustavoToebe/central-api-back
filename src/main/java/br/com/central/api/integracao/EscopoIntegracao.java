package br.com.central.api.integracao;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** A assinatura identifica a chave; a autorização também restringe o produto no método da rota. */
@Component("escopoIntegracao")
public class EscopoIntegracao {
    private final IntegracaoProperties properties;
    public EscopoIntegracao(IntegracaoProperties properties){this.properties=properties;properties.produtos();properties.chaves();}
    public boolean autorizado(Authentication auth,String produto) {
        if(auth==null || !auth.isAuthenticated() || produto==null || !(auth.getPrincipal() instanceof String principal)
            || !principal.startsWith("integracao:"))return false;
        String permitido=properties.produtos().get(principal.substring("integracao:".length()));
        return permitido!=null && permitido.equalsIgnoreCase(produto);
    }
}
