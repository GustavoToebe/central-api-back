package br.com.central.api.financeiro;

import br.com.central.api.operador.OperadorAuditoria;
import br.com.central.api.security.OperadorAutenticado;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
public class AuditoriaFinanceira {
    private final OperadorAuditoria audit;
    public AuditoriaFinanceira(OperadorAuditoria audit) {this.audit = audit;}
    public void registrar(String acao, String entidade, UUID id, List<String> campos) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        UUID operadorId = auth != null && auth.getPrincipal() instanceof OperadorAutenticado op ? op.id() : null;
        audit.registrarNaTransacao(operadorId, acao, entidade + ":" + id + ";campos=" + String.join(",", campos), null);
    }
}
