package br.com.central.api.integracao;
import br.com.central.api.comercial.ContratacaoRepository;
import br.com.central.api.web.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** Carrega apenas escalares em transação curta; o HTTP ocorre depois, em outro bean. */
@Service
public class ConsumoDestinoService {
 private final ContratacaoRepository repo;
 public ConsumoDestinoService(ContratacaoRepository repo) {this.repo=repo;}
 @Transactional(readOnly=true) public Destino carregar(UUID id) {
  var c=repo.buscarComReferencias(id);
  if(c==null) throw new ResourceNotFoundException("Contratação não encontrada.");
  if(c.getIdExterno()==null) throw new BadRequestException("A contratação ainda não foi provisionada.");
  return new Destino(c.getId(),c.getIdExterno(),c.getProduto().getUrlBaseIntegracao());
 }
 public record Destino(UUID contratacaoId,UUID tenantId,String url) { }
}
