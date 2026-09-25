package br.com.central.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Painel e API em subdomínios diferentes: o front só lê o {@code XSRF-TOKEN}
 * se o cookie for do domínio pai. Sem domínio, o cookie fica só no host da API.
 */
class CsrfCookieDomainTest {

    @Test
    void comDominioOCookieXsrfSaiNoDominioPai() {
        String setCookie = setCookieCom(" servirea.com.br ");

        assertThat(setCookie).startsWith("XSRF-TOKEN=");
        assertThat(setCookie).containsIgnoringCase("Domain=servirea.com.br");
        assertThat(setCookie).doesNotContainIgnoringCase("HttpOnly");
    }

    @Test
    void semDominioOCookieFicaSoNoHostDaApi() {
        assertThat(setCookieCom("")).doesNotContainIgnoringCase("Domain=");
        assertThat(setCookieCom(null)).doesNotContainIgnoringCase("Domain=");
    }

    private static String setCookieCom(String dominio) {
        SecurityProperties properties = new SecurityProperties(
                new SecurityProperties.Jwt("x", Duration.ofMinutes(15)),
                Duration.ofDays(30),
                new SecurityProperties.Cors(List.of("http://localhost:4200")),
                new SecurityProperties.Csrf(dominio));
        CookieCsrfTokenRepository repositorio = SecurityConfig.csrfTokenRepository(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        CsrfToken token = repositorio.generateToken(request);
        repositorio.saveToken(token, request, response);
        return response.getHeader("Set-Cookie");
    }
}
