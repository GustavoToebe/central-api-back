package br.com.central.api.monitoramento;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import org.springframework.mock.web.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import java.util.*;
import br.com.central.api.integracao.InstanciasVisaoService;
import static org.mockito.Mockito.*;
import org.springframework.security.core.context.SecurityContextHolder;
class MetricasTest {
    private final String segredo="teste_exclusivo_de_metricas_1234567890";
    @AfterEach void limpar() {
        SecurityContextHolder.clearContext();
    }
    @Test void coletaAgregaRotasSemDadosPessoais() {
        var r=new SimpleMeterRegistry();
        r.timer("http.server.requests","method","GET","status","200","uri","/pessoas/segredo").record(100,TimeUnit.MILLISECONDS);
        r.timer("http.server.requests","method","GET","status","201","uri","/outra").record(200,TimeUnit.MILLISECONDS);
        String s=new MetricasController(r,visao()).metricas().getBody();
        assertThat(s).contains("_count{metodo=\"GET\",status=\"2xx\"} 2","_sum").doesNotContain("pessoas","segredo","outra","uri=");
    }
    private InstanciasVisaoService visao() {
        var v=mock(InstanciasVisaoService.class);
        var porNivel=new EnumMap<InstanciasVisaoService.Nivel,Long>(InstanciasVisaoService.Nivel.class);
        porNivel.put(InstanciasVisaoService.Nivel.CRITICO,2L);porNivel.put(InstanciasVisaoService.Nivel.OK,7L);
        when(v.resumoParaMetricas()).thenReturn(new InstanciasVisaoService.Pagina(List.of(),9,0,30,porNivel,1));
        return v;
    }
    @Test void exportaInstanciasSomenteAgregadasPorNivel() {
        String s=new MetricasController(new SimpleMeterRegistry(),visao()).metricas().getBody();
        assertThat(s).contains("ecossistema_instancias_por_nivel{nivel=\"CRITICO\"} 2","ecossistema_instancias_por_nivel{nivel=\"OK\"} 7","ecossistema_instancias_defasadas 1");
        assertThat(s).doesNotContain("cliente=","contratacao","instancia=");
    }
    @Test void falhaNaLeituraDoPainelNaoDerrubaAsMetricasDeProcesso() {
        var v=mock(InstanciasVisaoService.class);when(v.resumoParaMetricas()).thenThrow(new IllegalStateException("banco fora"));
        String s=new MetricasController(new SimpleMeterRegistry(),v).metricas().getBody();
        assertThat(s).contains("ecossistema_http_servidor_duracao_seconds").doesNotContain("ecossistema_instancias");
    }
    @Test void filtroAceitaSomenteColetaERestauraContexto()throws Exception {
        var req=new MockHttpServletRequest("GET","/monitoramento/metrics");
        req.setServletPath("/monitoramento/metrics");
        req.addHeader("Authorization","Monitoring "+segredo);
        var res=new MockHttpServletResponse();
        new MonitoramentoFilter(segredo).doFilter(req,res,(a,b)->assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities()).extracting(Object::toString).containsExactly("ROLE_MONITORAMENTO"));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
    @Test void semConfiguracaoOuComTokenErradoNaoColeta()throws Exception {
        for(String token:new String[] {
            "",segredo
        }
        ) {
            var req=new MockHttpServletRequest("GET","/monitoramento/metrics");
            req.setServletPath("/monitoramento/metrics");
            req.addHeader("Authorization","Bearer qualquer");
            var res=new MockHttpServletResponse();
            new MonitoramentoFilter(token).doFilter(req,res,(a,b)-> {
                throw new AssertionError("não deve encaminhar");
            }
            );
            assertThat(res.getStatus()).isEqualTo(401);
        }
    }
    @Test void tokenDeColetaNaoAutenticaNegocio()throws Exception {
        var req=new MockHttpServletRequest("GET","/clientes");
        req.setServletPath("/clientes");
        req.addHeader("Authorization","Monitoring "+segredo);
        new MonitoramentoFilter(segredo).doFilter(req,new MockHttpServletResponse(),(a,b)->assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull());
    }
    @Test void postNaoEhColeta()throws Exception {
        var req=new MockHttpServletRequest("POST","/monitoramento/metrics");
        req.setServletPath("/monitoramento/metrics");
        req.addHeader("Authorization","Monitoring "+segredo);
        var res=new MockHttpServletResponse();
        new MonitoramentoFilter(segredo).doFilter(req,res,(a,b)-> {
            throw new AssertionError();
        }
        );
        assertThat(res.getStatus()).isEqualTo(401);
    }
}
