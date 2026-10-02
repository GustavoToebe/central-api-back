package br.com.central.api.integracao;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import br.com.central.api.web.ApiException;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.time.Instant;
/** Consulta sob demanda, sem cache nem fan-out; falha nunca se transforma em consumo zero. */
@Service
public class ConsumoCentralService {
 private final ConsumoDestinoService destinos;private final AplicativoHttp http;private final JsonMapper json;
 public ConsumoCentralService(ConsumoDestinoService destinos,AplicativoHttp http,JsonMapper json) {this.destinos=destinos;this.http=http;this.json=json;}
 public Resposta consultar(UUID id) {
  var d=destinos.carregar(id);
  try {
   var resposta=http.enviar(d.url(),"GET","/integracao/v1/instancias/"+d.tenantId()+"/consumo",null,null);
   if(resposta.status()!=200) throw new IllegalStateException();
   var n=json.readTree(resposta.corpo());
   if(n.path("versaoContrato").asInt()!=1||!d.tenantId().toString().equals(n.path("tenantId").asString())||!id.toString().equals(n.path("contratacaoId").asString())) throw new IllegalStateException();
   var consumo=json.treeToValue(n.path("consumo"),Consumo.class);
   if(consumo==null||consumo.itens()==null||consumo.itens().size()>32||consumo.consultadoEm()==null) throw new IllegalStateException();
   for(var item:consumo.itens()) if(item.usado()<0||item.pendentes()<0||item.limite()!=null&&item.limite()<0) throw new IllegalStateException();
   List<String> funcionalidades=new ArrayList<>();
   for(var f:n.path("funcionalidades")) if(f.isString()&&f.asString().matches("[A-Z_]{1,64}")) funcionalidades.add(f.asString());
   return new Resposta(id,consumo,List.copyOf(funcionalidades));
  } catch(RuntimeException e) {throw new ConsumoIndisponivel();}
 }
 public static class ConsumoIndisponivel extends ApiException { public ConsumoIndisponivel(){super(HttpStatus.BAD_GATEWAY,"Não foi possível consultar o consumo do aplicativo. Tente novamente.","CONSUMO_INDISPONIVEL");} }
 public record Item(String codigo,String nome,long usado,Long limite,Long disponivel,String estado,String unidade,long pendentes,String competencia) { }
 public record Consumo(String planoNome,Integer versaoDireitos,Instant direitosConfirmadosEm,Instant consultadoEm,List<Item> itens) { }
 public record Resposta(UUID contratacaoId,Consumo consumo,List<String> funcionalidades) { }
}
