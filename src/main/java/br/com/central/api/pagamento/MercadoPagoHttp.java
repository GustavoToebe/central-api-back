package br.com.central.api.pagamento;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.net.URI;
import java.util.*;

/** Adaptador de checkout hospedado. Nenhum dado de cartão passa pela Central. */
@Component
public class MercadoPagoHttp {
    private static final String BASE="https://api.mercadopago.com";
    private final RestClient http;
    private final String token,retorno,notificacao;
    @org.springframework.beans.factory.annotation.Autowired
    public MercadoPagoHttp(
        @Value("${central.mercadopago.access-token:}") String token,
        @Value("${central.mercadopago.retorno-url:}") String retorno,
        @Value("${central.mercadopago.notificacao-url:}") String notificacao) {
        this(RestClient.builder().requestFactory(fabrica()).build(),token,retorno,notificacao);
    }
    MercadoPagoHttp(RestClient http,String token,String retorno,String notificacao) {
        this.http=http;this.token=token;this.retorno=retorno;this.notificacao=notificacao;
    }
    public boolean configurado() { return token!=null && !token.isBlank() && https(retorno) && https(notificacao); }
    public record Checkout(String id,String url) {}
    public record Pagamento(String id,String referencia,String status,BigDecimal valor,String moeda,
        String coletor,boolean producao,String aprovadoEm,String tipo) {}

    public Checkout criarCheckout(UUID tentativa,BigDecimal valor) {
        exigirConfigurado();
        if (valor==null || valor.signum()<=0 || valor.scale()>2) throw new IllegalArgumentException("Valor de checkout inválido.");
        Map<String,Object> corpo=Map.of(
            "items",List.of(Map.of("id",tentativa.toString(),"title","Assinatura","quantity",1,"currency_id","BRL","unit_price",valor)),
            "external_reference",tentativa.toString(),"notification_url",notificacao,
            "back_urls",Map.of("success",retorno,"pending",retorno,"failure",retorno),"auto_return","approved");
        try {
            JsonNode resposta=http.post().uri(BASE+"/checkout/preferences")
                .header("Authorization","Bearer "+token).header("X-Idempotency-Key",tentativa.toString())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(corpo).retrieve().body(JsonNode.class);
            String id=texto(resposta,"id"),url=texto(resposta,"init_point");
            URI uri=URI.create(url);
            if (id.isBlank() || !"https".equals(uri.getScheme()) || !Set.of("www.mercadopago.com.br","www.mercadopago.com").contains(uri.getHost()) || uri.getUserInfo()!=null)
                throw new IllegalStateException("Resposta de checkout inválida.");
            return new Checkout(id,url);
        } catch (RestClientException ex) { throw new IllegalStateException("Mercado Pago indisponível para criar checkout."); }
    }
    public Pagamento consultarPagamento(String id) {
        exigirConfigurado();
        if (id==null || !id.matches("[0-9]{1,30}")) throw new IllegalArgumentException("Identificador de pagamento inválido.");
        try {
            JsonNode r=http.get().uri(BASE+"/v1/payments/"+id).header("Authorization","Bearer "+token).retrieve().body(JsonNode.class);
            if (!id.equals(texto(r,"id"))) throw new IllegalStateException("Resposta de pagamento inválida.");
            return new Pagamento(id,texto(r,"external_reference"),texto(r,"status"),
                new BigDecimal(texto(r,"transaction_amount")),texto(r,"currency_id"),texto(r,"collector_id"),
                r.path("live_mode").asBoolean(),texto(r,"date_approved"),texto(r,"payment_type_id"));
        } catch (RestClientException ex) { throw new IllegalStateException("Mercado Pago indisponível para conciliação."); }
    }
    private void exigirConfigurado() { if (!configurado()) throw new IllegalStateException("Configure as credenciais e URLs do Mercado Pago."); }
    private static org.springframework.http.client.JdkClientHttpRequestFactory fabrica() {
        var client=java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
        var factory=new org.springframework.http.client.JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(java.time.Duration.ofSeconds(20));return factory;
    }
    private static String texto(JsonNode n,String campo) { return n==null ? "" : n.path(campo).asText(""); }
    private static boolean https(String valor) {
        try { URI uri=URI.create(valor);return "https".equals(uri.getScheme()) && uri.getHost()!=null && uri.getUserInfo()==null && uri.getFragment()==null; }
        catch (RuntimeException ex) { return false; }
    }
}
