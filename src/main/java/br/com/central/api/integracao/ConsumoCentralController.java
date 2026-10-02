package br.com.central.api.integracao;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.UUID;
@RestController
public class ConsumoCentralController {
 private final ConsumoCentralService service;
 public ConsumoCentralController(ConsumoCentralService service) {this.service=service;}
 @GetMapping("/contratacoes/{id}/consumo") @PreAuthorize("hasAuthority('ROLE_OPERADOR')")
 public ConsumoCentralService.Resposta consultar(@PathVariable UUID id) {return service.consultar(id);}
}
