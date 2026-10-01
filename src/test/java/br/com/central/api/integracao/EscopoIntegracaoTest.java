package br.com.central.api.integracao;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class EscopoIntegracaoTest {
    private String chave(String id,String produto){return id+":"+Base64.getEncoder().encodeToString(new byte[32])+":"+produto;}
    @Test void cadaChaveSoPodeSeuProdutoELegadoNaoConcedeTodos(){
        var config=new IntegracaoProperties(chave("a","SERVIREA")+","+chave("b","OUTRO"),null,null);
        var escopo=new EscopoIntegracao(config);
        var a=UsernamePasswordAuthenticationToken.authenticated("integracao:a",null,List.of());
        var b=UsernamePasswordAuthenticationToken.authenticated("integracao:b",null,List.of());
        assertThat(escopo.autorizado(a,"servirea")).isTrue();assertThat(escopo.autorizado(a,"OUTRO")).isFalse();
        assertThat(escopo.autorizado(b,"OUTRO")).isTrue();assertThat(escopo.autorizado(b,"SERVIREA")).isFalse();
        assertThat(escopo.autorizado(null,"SERVIREA")).isFalse();
        var legado=new IntegracaoProperties("a:"+Base64.getEncoder().encodeToString(new byte[32]),null,null);
        assertThat(legado.produtos()).containsEntry("a","SERVIREA");assertThat(config.chaves()).hasSize(2);
        assertThat(config.toString()).doesNotContain(config.chavesEntrada());
    }
    @Test void escopoVazioOuDuplicadoFalhaNaConfiguracao(){
        assertThatThrownBy(() -> new EscopoIntegracao(new IntegracaoProperties(chave("a",""),null,null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EscopoIntegracao(new IntegracaoProperties(chave("a","SERVIREA")+","+chave("a","OUTRO"),null,null))).isInstanceOf(IllegalArgumentException.class);
    }
}
