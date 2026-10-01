package br.com.central.api.integracao;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Exercita o filtro real sem banco; os testes HTTP existentes continuam cobrindo persistência do nonce. */
class IntegracaoFiltroTest {
    private static final byte[] SEGREDO = "segredo-unitario-01234567890123456789".getBytes(StandardCharsets.UTF_8);
    private final IntegracaoNonceService nonces = mock(IntegracaoNonceService.class);
    private final IntegracaoProperties properties = new IntegracaoProperties("teste:" + Base64.getEncoder().encodeToString(SEGREDO), null, null);
    private final IntegracaoFiltro filtro = new IntegracaoFiltro(properties, nonces, JsonMapper.builder().build());
    private final FilterChain chain = mock(FilterChain.class);
    private final MockHttpServletResponse resposta = new MockHttpServletResponse();

    @AfterEach void limpar() { SecurityContextHolder.clearContext(); }

    private MockHttpServletRequest pedido(byte[] corpo) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/integracao/v1/instancias");
        req.setServletPath("/integracao/v1/instancias");
        req.setContent(corpo);
        String instante = Long.toString(Instant.now().getEpochSecond());
        req.addHeader(IntegracaoFiltro.CHAVE, "teste");
        req.addHeader(IntegracaoFiltro.TIMESTAMP, instante);
        req.addHeader(IntegracaoFiltro.NONCE, "nonce-unitario");
        req.addHeader(IntegracaoFiltro.ASSINATURA, HmacAssinatura.assinar(
                SEGREDO, "POST", req.getRequestURI(), instante, "nonce-unitario", corpo));
        return req;
    }

    @Test void chaveDesconhecidaNaoLeOCorpo() throws Exception {
        MockHttpServletRequest req = spy(pedido(new byte[0]));
        req.removeHeader(IntegracaoFiltro.CHAVE);
        req.addHeader(IntegracaoFiltro.CHAVE, "desconhecida");
        filtro.doFilter(req, resposta, chain);
        assertThat(resposta.getStatus()).isEqualTo(401);
        verify(req, never()).getInputStream();
        verifyNoInteractions(nonces, chain);
    }

    @Test void timestampExtremoNaoPassaPelaJanelaNemLeOCorpo() throws Exception {
        MockHttpServletRequest req = spy(pedido(new byte[0]));
        req.removeHeader(IntegracaoFiltro.TIMESTAMP);
        req.addHeader(IntegracaoFiltro.TIMESTAMP, Long.toString(Long.MIN_VALUE));
        filtro.doFilter(req, resposta, chain);
        assertThat(resposta.getStatus()).isEqualTo(401);
        verify(req, never()).getInputStream();
        verifyNoInteractions(nonces, chain);
    }

    @Test void assinaturaMalformadaNaoLeOCorpo() throws Exception {
        MockHttpServletRequest req = spy(pedido(new byte[0]));
        req.removeHeader(IntegracaoFiltro.ASSINATURA);
        req.addHeader(IntegracaoFiltro.ASSINATURA, "invalida");
        filtro.doFilter(req, resposta, chain);
        assertThat(resposta.getStatus()).isEqualTo(401);
        verify(req, never()).getInputStream();
        verifyNoInteractions(nonces, chain);
    }

    @Test void tamanhoDeclaradoAcimaDoLimiteRecusaSemLeitura() throws Exception {
        MockHttpServletRequest req = spy(pedido(new byte[0]));
        when(req.getContentLengthLong()).thenReturn((long) IntegracaoFiltro.MAX_CORPO_BYTES + 1);
        filtro.doFilter(req, resposta, chain);
        assertThat(resposta.getStatus()).isEqualTo(413);
        assertThat(resposta.getContentAsString()).contains("CORPO_EXCEDIDO");
        verify(req, never()).getInputStream();
        verifyNoInteractions(nonces, chain);
    }

    @Test void corpoSemContentLengthTambemTemLimite() throws Exception {
        MockHttpServletRequest req = spy(pedido(new byte[IntegracaoFiltro.MAX_CORPO_BYTES + 1]));
        when(req.getContentLengthLong()).thenReturn(-1L);
        filtro.doFilter(req, resposta, chain);
        assertThat(resposta.getStatus()).isEqualTo(413);
        verifyNoInteractions(nonces, chain);
    }

    @Test void assinaturaIncorretaNaoConsomeNonce() throws Exception {
        MockHttpServletRequest req = pedido(new byte[0]);
        req.setContent("alterado".getBytes(StandardCharsets.UTF_8));
        filtro.doFilter(req, resposta, chain);
        assertThat(resposta.getStatus()).isEqualTo(401);
        verifyNoInteractions(nonces, chain);
    }

    @Test void corpoNoLimitePassaIntegroParaOController() throws Exception {
        byte[] corpo = new byte[IntegracaoFiltro.MAX_CORPO_BYTES];
        when(nonces.registrar("teste", "nonce-unitario")).thenReturn(true);
        AtomicReference<byte[]> recebido = new AtomicReference<>();
        filtro.doFilter(pedido(corpo), resposta, (req, res) -> recebido.set(req.getInputStream().readAllBytes()));
        assertThat(recebido.get()).isEqualTo(corpo);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority").containsExactly(IntegracaoFiltro.AUTHORITY);
    }

    @Test void nonceRepetidoNaoChegaAoController() throws Exception {
        filtro.doFilter(pedido(new byte[0]), resposta, chain);
        assertThat(resposta.getStatus()).isEqualTo(401);
        assertThat(resposta.getContentAsString()).contains("NONCE_REPETIDO");
        verifyNoInteractions(chain);
    }
}
