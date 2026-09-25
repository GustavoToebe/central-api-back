package br.com.central.api.security;

import br.com.central.api.integracao.IntegracaoFiltro;
import br.com.central.api.integracao.IntegracaoProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Set;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({SecurityProperties.class, IntegracaoProperties.class})
public class SecurityConfig {

    private static final Set<String> METODOS_SEGUROS = Set.of("GET", "HEAD", "TRACE", "OPTIONS");

    /** /integracao/** não entra aqui: o HMAC autentica e o controller exige PERM_INTEGRACAO. */
    private static final String[] ROTAS_PUBLICAS = {"/auth/**", "/actuator/health", "/error"};

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.cors().allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("X-Request-Id"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                     JwtAuthenticationFilter jwtAuthenticationFilter,
                                                     IntegracaoFiltro integracaoFiltro,
                                                     CorsConfigurationSource corsConfigurationSource,
                                                     RestAuthenticationEntryPoint authenticationEntryPoint,
                                                     RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf
                        .spa()
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .requireCsrfProtectionMatcher(SecurityConfig::exigeCsrf)
                        .ignoringRequestMatchers("/auth/login", "/integracao/**"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(ROTAS_PUBLICAS).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(integracaoFiltro, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** Refresh e logout usam o cookie. Login e integração não. Bearer dispensa CSRF. */
    static boolean exigeCsrf(HttpServletRequest request) {
        if (METODOS_SEGUROS.contains(request.getMethod())) {
            return false;
        }
        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        if (caminho.startsWith("/auth/")) {
            return true;
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return false;
        }
        // Sem Bearer, o CSRF só protege quem já tem o cookie de sessão.
        // Pedido anônimo segue para a autenticação e responde 401.
        return temCookie(request, br.com.central.api.operador.AuthController.REFRESH_TOKEN_COOKIE);
    }

    private static boolean temCookie(HttpServletRequest request, String nome) {
        if (request.getCookies() == null) {
            return false;
        }
        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if (nome.equals(cookie.getName())) {
                return true;
            }
        }
        return false;
    }
}
