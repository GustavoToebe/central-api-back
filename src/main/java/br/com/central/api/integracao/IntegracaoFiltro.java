package br.com.central.api.integracao;

import br.com.central.api.web.ApiError;
import br.com.central.api.web.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Valida HMAC nas rotas {@code /integracao/**}, na ordem do contrato:
 * chave, janela de 300s, assinatura, nonce em transação própria.
 *
 * <p>Quem passa ganha {@code PERM_INTEGRACAO}. A rota não é {@code permitAll}:
 * um caminho que escape deste filtro ({@code /%69ntegracao/...}) chega sem
 * autenticação e recebe 401. A decisão de filtrar usa o caminho decodificado
 * ({@code servletPath}); a assinatura usa o caminho cru.</p>
 */
@Component
public class IntegracaoFiltro extends OncePerRequestFilter {

    static final String CHAVE = "X-Integracao-Chave";
    static final String TIMESTAMP = "X-Integracao-Timestamp";
    static final String NONCE = "X-Integracao-Nonce";
    static final String ASSINATURA = "X-Integracao-Assinatura";

    public static final String AUTHORITY = "PERM_INTEGRACAO";

    private static final long JANELA_SEGUNDOS = 300;
    private static final Logger log = LoggerFactory.getLogger(IntegracaoFiltro.class);

    private final IntegracaoProperties properties;
    private final IntegracaoNonceService nonceService;
    private final JsonMapper jsonMapper;

    public IntegracaoFiltro(IntegracaoProperties properties,
                             IntegracaoNonceService nonceService,
                             JsonMapper jsonMapper) {
        this.properties = properties;
        this.nonceService = nonceService;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !caminhoDecodificado(request).startsWith("/integracao/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        byte[] corpo = request.getInputStream().readAllBytes();
        String chaveId = request.getHeader(CHAVE);
        byte[] segredo = properties.chaves().get(chaveId);
        if (segredo == null) {
            recusar(response, request, "CHAVE_DESCONHECIDA", "chave=" + chaveId);
            return;
        }
        String timestamp = request.getHeader(TIMESTAMP);
        long epoch;
        try {
            epoch = Long.parseLong(timestamp);
        } catch (RuntimeException e) {
            recusar(response, request, "TIMESTAMP_FORA_DA_JANELA", "timestamp ilegível");
            return;
        }
        long agora = Instant.now().getEpochSecond();
        if (Math.abs(agora - epoch) > JANELA_SEGUNDOS) {
            recusar(response, request, "TIMESTAMP_FORA_DA_JANELA", "delta=" + (agora - epoch));
            return;
        }
        String nonce = request.getHeader(NONCE);
        String caminho = request.getRequestURI();
        if (request.getQueryString() != null) {
            caminho = caminho + "?" + request.getQueryString();
        }
        String esperada = HmacAssinatura.assinar(segredo, request.getMethod(), caminho, timestamp, nonce, corpo);
        if (!HmacAssinatura.confere(esperada, request.getHeader(ASSINATURA))) {
            recusar(response, request, "ASSINATURA_INVALIDA", "caminho=" + caminho);
            return;
        }
        if (nonce == null || nonce.isBlank() || !nonceService.registrar(chaveId, nonce)) {
            recusar(response, request, "NONCE_REPETIDO", "nonce=" + nonce);
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "integracao:" + chaveId, null, List.of(new SimpleGrantedAuthority(AUTHORITY))));
        chain.doFilter(new CorpoCacheado(request, corpo), response);
    }

    private static String caminhoDecodificado(HttpServletRequest request) {
        String servletPath = request.getServletPath() == null ? "" : request.getServletPath();
        String pathInfo = request.getPathInfo() == null ? "" : request.getPathInfo();
        return (servletPath + pathInfo).toLowerCase(Locale.ROOT);
    }

    private void recusar(HttpServletResponse response, HttpServletRequest request, String codigo, String detalhe)
            throws IOException {
        log.warn("Integração recusada: {} ({})", codigo, detalhe);
        ApiError body = new ApiError(
                Instant.now(), 401, "UNAUTHORIZED", "Requisição de integração recusada.", codigo,
                request.getRequestURI(), MDC.get(RequestIdFilter.MDC_KEY), null);
        response.setStatus(401);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getWriter(), body);
    }

    private static final class CorpoCacheado extends HttpServletRequestWrapper {
        private final byte[] corpo;

        private CorpoCacheado(HttpServletRequest request, byte[] corpo) {
            super(request);
            this.corpo = corpo;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(corpo);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
