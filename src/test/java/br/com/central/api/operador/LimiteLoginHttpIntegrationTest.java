package br.com.central.api.operador;
import br.com.central.api.AbstractIntegrationTest;
import br.com.central.api.web.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@AutoConfigureMockMvc
@TestPropertySource(properties={"central.auth.limite-conta=2","central.auth.limite-ip=3"})
class LimiteLoginHttpIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean AuthService auth;
    @Test void limiteDeContaResponde429AntesDoServico() throws Exception {
        when(auth.entrar(anyString(),anyString(),anyString())).thenThrow(new UnauthorizedException("Acesso inválido."));
        String email="a"+java.util.UUID.randomUUID()+"@teste.com";
        for(int n=0;n<3;n++) {
            var resposta=mvc.perform(post("/auth/login").contentType("application/json")
                .content("{\"email\":\""+email+"\",\"senha\":\"senha-invalida\"}").with(req -> {req.setRemoteAddr("198.51.100.20");return req;}));
            if(n<2)resposta.andExpect(status().isUnauthorized());
            else resposta.andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After")).andExpect(jsonPath("$.codigo").value("LOGIN_LIMITADO"));
        }
        verify(auth,times(2)).entrar(anyString(),anyString(),anyString());
    }    @Test void ipLimitaContasDiferentesMesmoComHeaderForjado() throws Exception {
        when(auth.entrar(anyString(),anyString(),anyString())).thenThrow(new UnauthorizedException("Acesso inválido."));
        for(int n=0;n<4;n++) {
            String email="a"+java.util.UUID.randomUUID()+"@teste.com";
            var resposta=mvc.perform(post("/auth/login").contentType("application/json")
                .content("{\"email\":\""+email+"\",\"senha\":\"senha-invalida\"}")
                .with(req -> {req.setRemoteAddr("198.51.100.21");return req;}).header("X-Forwarded-For","203.0.113."+n));
            resposta.andExpect(n<3 ? status().isUnauthorized() : status().isTooManyRequests());
        }
        verify(auth,times(3)).entrar(anyString(),anyString(),anyString());
    }

}
