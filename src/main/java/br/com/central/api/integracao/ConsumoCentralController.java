package br.com.central.api.integracao;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.UUID;
@RestController
public class ConsumoCentralController {
 private final ConsumoCentralService service;private final ConsumoHistoricoService historico;
 public ConsumoCentralController(ConsumoCentralService service,ConsumoHistoricoService historico) {this.service=service;this.historico=historico;}
 @GetMapping("/contratacoes/{id}/consumo/historico") @PreAuthorize("hasAuthority('ROLE_OPERADOR')")
 public java.util.List<ConsumoHistoricoService.Ponto> historico(@PathVariable UUID id){return historico.listar(id);}
 @GetMapping("/contratacoes/{id}/consumo") @PreAuthorize("hasAuthority('ROLE_OPERADOR')")
 public ConsumoCentralService.Resposta consultar(@PathVariable UUID id) {return service.consultar(id);}
}
