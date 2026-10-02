package br.com.central.api.integracao;
import br.com.central.api.comercial.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
/** Última consulta bem-sucedida de cada dia, por 90 dias. Ausência não equivale a zero. */
@Service
public class ConsumoHistoricoService {
 private final ContratacaoRepository contratacoes;private final JsonMapper json;
 @PersistenceContext private EntityManager em;
 public ConsumoHistoricoService(ContratacaoRepository contratacoes,JsonMapper json){this.contratacoes=contratacoes;this.json=json;}
 @Transactional public void registrar(UUID id,ConsumoCentralService.Consumo consumo){
  contratacoes.buscarParaAlterar(id).orElseThrow();
  var dia=consumo.consultadoEm().atZone(ZoneId.of("America/Sao_Paulo")).toLocalDate();
  var h=em.createQuery("select h from ConsumoHistorico h where h.escopo=:id and h.dia=:dia",ConsumoHistorico.class).setParameter("id",id).setParameter("dia",dia).getResultStream().findFirst().orElse(null);
  if(h!=null&&!consumo.consultadoEm().isAfter(h.consultadoEm))return;
  String dados=json.writeValueAsString(consumo);if(dados.length()>32768)throw new IllegalStateException("Resumo de consumo excessivo.");
  boolean novo=h==null;if(novo){h=new ConsumoHistorico();h.escopo=id;h.dia=dia;}h.consultadoEm=consumo.consultadoEm();h.dados=dados;if(novo)em.persist(h);
  em.createQuery("delete from ConsumoHistorico h where h.escopo=:id and h.dia<:limite").setParameter("id",id).setParameter("limite",LocalDate.now(ZoneId.of("America/Sao_Paulo")).minusDays(89)).executeUpdate();
 }
 @Transactional(readOnly=true) public List<Ponto> listar(UUID id){
  return em.createQuery("select h from ConsumoHistorico h where h.escopo=:id and h.dia>=:limite order by h.dia desc",ConsumoHistorico.class).setParameter("id",id).setParameter("limite",LocalDate.now(ZoneId.of("America/Sao_Paulo")).minusDays(89)).setMaxResults(90).getResultList().stream().map(h->new Ponto(h.dia,json.readValue(h.dados,ConsumoCentralService.Consumo.class))).toList();
 }
 public record Ponto(LocalDate dia,ConsumoCentralService.Consumo consumo){}
}
