package br.com.central.api.security;

import br.com.central.api.operador.Operador;
import br.com.central.api.operador.OperadorRepository;
import br.com.central.api.web.UnauthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Sem Bearer, não mexe no contexto: o filtro HMAC pode já ter autenticado
 * a chamada de integração. Token inválido ou operador inativo também não
 * autentica — a rota protegida responde 401.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ROLE_OPERADOR = "ROLE_OPERADOR";

    private final JwtService jwtService;
    private final OperadorRepository operadorRepository;

    public JwtAuthenticationFilter(JwtService jwtService, OperadorRepository operadorRepository) {
        this.jwtService = jwtService;
        this.operadorRepository = operadorRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            chain.doFilter(request, response);
            return;
        }
        try {
            Operador operador = operadorRepository.findById(jwtService.validarAccessToken(header.substring(7)))
                    .filter(Operador::isAtivo)
                    .orElse(null);
            if (operador != null) {
                var autenticado = new OperadorAutenticado(operador.getId(), operador.getEmail());
                var autenticacao = new UsernamePasswordAuthenticationToken(
                        autenticado, null, List.of(new SimpleGrantedAuthority(ROLE_OPERADOR)));
                SecurityContextHolder.getContext().setAuthentication(autenticacao);
            }
        } catch (UnauthorizedException ignorado) {
            // token recusado: segue sem autenticar o operador
        }
        chain.doFilter(request, response);
    }
}
