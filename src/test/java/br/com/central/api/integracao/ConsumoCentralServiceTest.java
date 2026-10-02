package br.com.central.api.integracao;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;
import br.com.central.api.web.ApiException;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class ConsumoCentralServiceTest {
 ConsumoDestinoService destinos=mock(ConsumoDestinoService.class);AplicativoHttp http=mock(AplicativoHttp.class);
 UUID id=UUID.randomUUID(),tenant=UUID.randomUUID();ConsumoCentralService service=new ConsumoCentralService(destinos,http,JsonMapper.builder().findAndAddModules().build());
 @BeforeEach void preparar(){when(destinos.carregar(id)).thenReturn(new ConsumoDestinoService.Destino(id,tenant,"https://app.example.test"));}
 byte[] corpo(UUID contrato){return ("{\"versaoContrato\":1,\"tenantId\":\""+tenant+"\",\"contratacaoId\":\""+contrato+"\",\"consumo\":{\"planoNome\":\"Plano\",\"versaoDireitos\":1,\"direitosConfirmadosEm\":\"2026-10-01T00:00:00Z\",\"consultadoEm\":\"2026-10-01T01:00:00Z\",\"itens\":[{\"codigo\":\"pessoas\",\"nome\":\"Pessoas\",\"usado\":5,\"limite\":10,\"disponivel\":5,\"estado\":\"DISPONIVEL\",\"unidade\":\"unidade\",\"pendentes\":0,\"competencia\":null}]},\"funcionalidades\":[\"MURAL\"]}").getBytes(StandardCharsets.UTF_8);}
 void resposta(int status,byte[] body){when(http.enviar(anyString(),eq("GET"),anyString(),isNull(),isNull())).thenReturn(new AplicativoHttp.Resposta(status,body));}
 @Test void consultaSomenteDestinoConfiguradoEDevolveContagens(){resposta(200,corpo(id));var r=service.consultar(id);assertThat(r.consumo().itens().getFirst().usado()).isEqualTo(5);assertThat(r.funcionalidades()).containsExactly("MURAL");verify(http).enviar("https://app.example.test","GET","/integracao/v1/instancias/"+tenant+"/consumo",null,null);}
 @Test void rejeitaContratoDeOutraInstancia(){resposta(200,corpo(UUID.randomUUID()));assertThatThrownBy(()->service.consultar(id)).isInstanceOf(ApiException.class);}
 @Test void naoConverteErroHttpEmConsumoZero(){resposta(503,new byte[0]);assertThatThrownBy(()->service.consultar(id)).isInstanceOf(ApiException.class);}
 @Test void naoAceitaFormatoAntigoComPlaceholder(){resposta(200,"{\"uso\":{\"armazenamento_mb\":0}}".getBytes(StandardCharsets.UTF_8));assertThatThrownBy(()->service.consultar(id)).isInstanceOf(ApiException.class);}
 @Test void redeIndisponivelNaoExibeDadosAntigos(){when(http.enviar(anyString(),anyString(),anyString(),isNull(),isNull())).thenThrow(new IllegalStateException("rede"));assertThatThrownBy(()->service.consultar(id)).isInstanceOf(ApiException.class);}
}
