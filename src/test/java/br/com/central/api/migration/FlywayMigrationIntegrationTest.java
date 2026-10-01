package br.com.central.api.migration;

import br.com.central.api.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void aplicouAMigrationInicial() {
        Integer total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE type = 'SQL' AND success = true", Integer.class);
        List<String> descricoes = jdbcTemplate.queryForList(
                "SELECT description FROM flyway_schema_history WHERE type = 'SQL' ORDER BY installed_rank",
                String.class);
        assertThat(total).isEqualTo(13);
        assertThat(descricoes).containsExactly("operador e nonce", "dominio comercial", "ultimo status provisionamento",
                "cep formatado", "periodicidades e itens da cobranca", "erro aplicativo", "sequencial",
                "fecha data api do supabase", "competencia cancelada libera nova cobranca", "isencao", "reserva do outbox", "checkout mercadopago", "mfa operador");
    }

    @Test
    void tabelasPublicasTemRlsSemPolicy() {
        Integer comRls = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM pg_tables t
                JOIN pg_class c ON c.relname = t.tablename
                JOIN pg_namespace n ON n.oid = c.relnamespace AND n.nspname = t.schemaname
                WHERE t.schemaname = 'public'
                  AND t.tablename IN (
                    'operador', 'operador_log', 'refresh_token', 'integracao_nonce',
                    'cliente', 'cliente_contato', 'produto', 'recurso', 'plano', 'plano_recurso',
                    'preco_plano', 'adicional', 'contratacao', 'contratacao_adicional',
                    'historico_contratacao', 'cobranca', 'cobranca_item', 'evento_saida', 'erro_aplicativo')
                  AND c.relrowsecurity = true
                """, Integer.class);
        Integer policies = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM pg_policies
                WHERE schemaname = 'public'
                  AND tablename IN (
                    'operador', 'operador_log', 'refresh_token', 'integracao_nonce',
                    'cliente', 'cliente_contato', 'produto', 'recurso', 'plano', 'plano_recurso',
                    'preco_plano', 'adicional', 'contratacao', 'contratacao_adicional',
                    'historico_contratacao', 'cobranca', 'cobranca_item', 'evento_saida', 'erro_aplicativo')
                """, Integer.class);
        assertThat(comRls).isEqualTo(19);
        assertThat(policies).isZero();
        Integer semRls = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p') AND NOT c.relrowsecurity
                  AND c.relname <> 'flyway_schema_history'
                """, Integer.class);
        assertThat(semRls).as("tabela nova no public sem RLS").isZero();
    }
}
