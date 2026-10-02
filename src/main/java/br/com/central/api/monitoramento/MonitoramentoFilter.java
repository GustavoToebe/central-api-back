package br.com.central.api.monitoramento;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
/** Credencial exclusiva de coleta. Não autentica outras rotas nem concede acesso ao negócio. */ public final class MonitoramentoFilter extends OncePerRequestFilter {
    private final byte[] esperado;
    public MonitoramentoFilter(String token) {
        esperado=token!=null&&token.matches("[A-Za-z0-9_-]{32,128}")?token.getBytes(StandardCharsets.UTF_8):null;
    }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException {
        if(!"/monitoramento/metrics".equals(req.getRequestURI().substring(req.getContextPath().length()))) {
            chain.doFilter(req,res);
            return;
        }
        String h=req.getHeader("Authorization");
        String recebido=h!=null&&h.startsWith("Monitoring ")?h.substring(11):"";
        if(!"GET".equals(req.getMethod())||esperado==null||recebido.length()>128||!MessageDigest.isEqual(esperado,recebido.getBytes(StandardCharsets.UTF_8))) {
            res.setStatus(401);
            res.setHeader("Cache-Control","no-store");
            return;
        }
        var anterior=SecurityContextHolder.getContext();
        var contexto=SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(new UsernamePasswordAuthenticationToken("coletor",null,List.of(new SimpleGrantedAuthority("ROLE_MONITORAMENTO"))));
        SecurityContextHolder.setContext(contexto);
        try {
            chain.doFilter(req,res);
        }
        finally {
            SecurityContextHolder.setContext(anterior);
        }
    }
}
