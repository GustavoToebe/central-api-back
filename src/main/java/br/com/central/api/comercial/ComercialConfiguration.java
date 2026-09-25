package br.com.central.api.comercial;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ComercialConfiguration {

    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock clock() {
        return Clock.system(FUSO);
    }
}
