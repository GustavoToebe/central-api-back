package br.com.central.api.integracao;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Configuration
public class IntegracaoClienteConfig {

    @Bean
    public AplicativoHttp aplicativoHttp(IntegracaoProperties properties, Clock clock) {
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        fabrica.setReadTimeout(Duration.ofSeconds(10));
        RestClient http = RestClient.builder().requestFactory(fabrica).build();
        return new AplicativoHttp(
                http,
                properties.chaveSaidaId(),
                decodificar(properties.chaveSaidaSegredo()),
                clock,
                () -> UUID.randomUUID().toString());
    }

    private static byte[] decodificar(String segredoBase64) {
        if (segredoBase64 == null || segredoBase64.isBlank()) {
            return new byte[0];
        }
        return Base64.getDecoder().decode(segredoBase64.trim());
    }
}
