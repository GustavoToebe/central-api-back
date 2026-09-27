package br.com.central.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/*
 * Sem o usuário em memória do Spring: o login é só por JWT (e HMAC na integração), e o usuário padrão só
 * gerava o aviso "Using generated security password" no log de produção (27/09/2026).
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
public class CentralApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CentralApiApplication.class, args);
    }
}
