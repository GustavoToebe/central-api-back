package br.com.central.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Em produção os segredos e a configuração da integração vêm só de variável
 * de ambiente, sem valor padrão: faltando a variável, a Central não sobe (em
 * vez de subir sem chave e falhar em toda chamada de integração).
 */
class ConfiguracaoProducaoTest {

    @Test
    void variaveisObrigatoriasDeProducaoNaoTemValorPadrao() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-prod.yml"));
        Properties prod = yaml.getObject();

        assertThat(prod).isNotNull();
        assertSemPadrao(prod, "central.security.jwt.secret", "CENTRAL_JWT_SEGREDO");
        assertSemPadrao(prod, "central.security.cors.allowed-origins", "CORS_ALLOWED_ORIGINS");
        assertSemPadrao(prod, "central.security.csrf.cookie-domain", "CENTRAL_CSRF_COOKIE_DOMAIN");
        assertSemPadrao(prod, "central.integracao.chaves-entrada", "CENTRAL_PRODUTO_SERVIRE_CHAVES_ENTRADA");
        assertSemPadrao(prod, "central.integracao.chave-saida-id", "CENTRAL_PRODUTO_SERVIRE_CHAVE_SAIDA_ID");
        assertSemPadrao(prod, "central.integracao.chave-saida-segredo", "CENTRAL_PRODUTO_SERVIRE_CHAVE_SAIDA_SEGREDO");
    }

    private static void assertSemPadrao(Properties prod, String chave, String variavel) {
        assertThat(prod.getProperty(chave)).as(chave).isEqualTo("${" + variavel + "}");
    }
}
