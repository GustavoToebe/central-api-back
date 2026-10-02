package br.com.central.api.monitoramento;
import br.com.central.api.AbstractIntegrationTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import java.net.URI;
import java.net.http.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties="monitoramento.token=credencial_local_de_teste_sem_uso_real_12345")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class MetricasHttpIntegrationTest extends AbstractIntegrationTest {
    @Value("${local.server.port}") int porta;
    private HttpResponse<String> consultar(String path,String auth)throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+porta+path)).GET();
        if(auth!=null)builder.header("Authorization",auth);
        return HttpClient.newHttpClient().send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void httpRealRegistraRequisicoesEApenasColetorLeAgregados()throws Exception {
        assertThat(consultar("/actuator/health",null).statusCode()).isEqualTo(200);
        assertThat(consultar("/monitoramento/metrics",null).statusCode()).isEqualTo(401);
        assertThat(consultar("/monitoramento/metrics","Monitoring errado").statusCode()).isEqualTo(401);
        var r=consultar("/monitoramento/metrics","Monitoring credencial_local_de_teste_sem_uso_real_12345");
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("ecossistema_http_servidor_duracao_seconds_count","ecossistema_jvm_memoria_bytes").doesNotContain("uri=","tenant=","/actuator/health","credencial_local");
        assertThat(r.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(consultar("/NAO_EXISTE_NEGOCIO","Monitoring credencial_local_de_teste_sem_uso_real_12345").statusCode()).isEqualTo(401);
    }
}
