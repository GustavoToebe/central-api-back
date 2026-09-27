package br.com.central.api.operador;

import br.com.central.api.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthHttpIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OperadorRepository operadorRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private String email;

    @BeforeEach
    void criarOperador() {
        email = "op-" + UUID.randomUUID() + "@central.test";
        operadorRepository.saveAndFlush(new Operador("Ana Operadora", email, passwordEncoder.encode("senha-correta-123")));
    }

    @Test
    void senhaErradaRecebe401() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"errada\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRefreshELogout() throws Exception {
        MvcResult login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha-correta-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.operador.email").value(email))
                .andReturn();
        String access = com.jayway.jsonpath.JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
        String cookie = valorDoCookie(login);

        mockMvc.perform(get("/operadores/eu").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        MvcResult refresh = mockMvc.perform(post("/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(AuthController.REFRESH_TOKEN_COOKIE, cookie))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        String cookieNovo = valorDoCookie(refresh);

        mockMvc.perform(post("/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie(AuthController.REFRESH_TOKEN_COOKIE, cookieNovo))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(AuthController.REFRESH_TOKEN_COOKIE, cookieNovo))
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void euSemTokenRecebe401EQuemNaoEOperadorRecebe403() throws Exception {
        mockMvc.perform(get("/operadores/eu"))
                .andExpect(status().isUnauthorized());

        var integracao = UsernamePasswordAuthenticationToken.authenticated(
                "integracao:teste", null, List.of(new SimpleGrantedAuthority("PERM_INTEGRACAO")));
        mockMvc.perform(get("/operadores/eu").with(authentication(integracao)))
                .andExpect(status().isForbidden());
    }

    @Test
    void trocaDeSenhaConfereAAtualDerrubaAsOutrasSessoesEMantemEsta() throws Exception {
        MvcResult login = entrar("senha-correta-123");
        String access = com.jayway.jsonpath.JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
        String cookieAntigo = valorDoCookie(login);
        String outraSessao = valorDoCookie(entrar("senha-correta-123"));

        mockMvc.perform(put("/operadores/eu/senha").header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"errada\",\"novaSenha\":\"nova-senha-456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A senha atual não confere."));
        mockMvc.perform(put("/operadores/eu/senha").header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"senha-correta-123\",\"novaSenha\":\"curta\"}"))
                .andExpect(status().isBadRequest());

        MvcResult troca = mockMvc.perform(put("/operadores/eu/senha").header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"senha-correta-123\",\"novaSenha\":\"nova-senha-456\"}"))
                .andExpect(status().isNoContent())
                .andReturn();
        String cookieNovo = valorDoCookie(troca);

        for (String derrubado : List.of(cookieAntigo, outraSessao)) {
            mockMvc.perform(post("/auth/refresh")
                            .cookie(new jakarta.servlet.http.Cookie(AuthController.REFRESH_TOKEN_COOKIE, derrubado))
                            .with(csrf()))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(AuthController.REFRESH_TOKEN_COOKIE, cookieNovo))
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha-correta-123\"}"))
                .andExpect(status().isUnauthorized());
        entrar("nova-senha-456");
    }

    private MvcResult entrar(String senha) throws Exception {
        return mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String valorDoCookie(MvcResult result) {
        return result.getResponse().getCookie(AuthController.REFRESH_TOKEN_COOKIE).getValue();
    }
}
