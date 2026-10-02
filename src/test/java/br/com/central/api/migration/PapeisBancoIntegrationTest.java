package br.com.central.api.migration;

import br.com.central.api.AbstractIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T16: reproduz o roteiro de docs/papeis-banco.md num banco descartável do Postgres 17 de teste. Banco já migrado pelo
 * administrador (como a produção hoje) → criação dos papéis → adoção (propriedade dos objetos vai para o migrador) →
 * nova migration aplicada SÓ com as credenciais do migrador → verificação do que a API (papel app) pode e não pode fazer.
 */
class PapeisBancoIntegrationTest extends AbstractIntegrationTest {
    @Value("${spring.datasource.url}") String urlPadrao;
    @Value("${spring.datasource.username}") String usuarioAdmin;
    @Value("${spring.datasource.password}") String senhaAdmin;

    private static String sql(String arquivo) throws Exception {
        return Files.readString(Path.of(arquivo), StandardCharsets.UTF_8);
    }

    private void admin(String banco, String comando) throws SQLException {
        try (Connection c = DriverManager.getConnection(urlDe(banco), usuarioAdmin, senhaAdmin); Statement st = c.createStatement()) {
            st.execute(comando);
        }
    }

    /** Mesma instância do Postgres de teste, outro banco. */
    private String urlDe(String banco) {
        int barra = urlPadrao.indexOf('/', "jdbc:postgresql://".length());
        int interrogacao = urlPadrao.indexOf('?', barra);
        String resto = interrogacao < 0 ? "" : urlPadrao.substring(interrogacao);
        return urlPadrao.substring(0, barra + 1) + banco + resto;
    }

    @Test
    void migradorEvoluiOSchemaEOAppSoOperaDados() throws Exception {
        String sufixo = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String banco = "papeis_" + sufixo;
        String migrador = "migrador_" + sufixo;
        String app = "app_" + sufixo;
        Path novaMigration = Files.createTempDirectory("migration-nova");
        Files.writeString(novaMigration.resolve("V9001__tabela_do_migrador.sql"),
                "CREATE TABLE nova_tabela(id uuid PRIMARY KEY, nome text);\n"
                        + "ALTER TABLE nova_tabela ENABLE ROW LEVEL SECURITY;\n"
                        + "ALTER TABLE cliente ADD COLUMN observacao_t16 text;\n");
        admin("postgres", "CREATE DATABASE " + banco);
        try {
            // 1. Banco já migrado pelo administrador, como a produção hoje.
            var inicial = Flyway.configure().dataSource(urlDe(banco), usuarioAdmin, senhaAdmin).locations("classpath:db/migration").load().migrate();
            assertThat(inicial.success).isTrue();

            // 2. Papéis e adoção.
            admin(banco, sql("scripts/roles-banco.sql").replace("{{banco}}", banco).replace("{{migrador}}", migrador)
                    .replace("{{app}}", app).replace("{{senha_migrador}}", "senha-migrador").replace("{{senha_app}}", "senha-app"));
            admin(banco, sql("scripts/adotar-papeis-banco.sql").replace("{{migrador}}", migrador));

            // 3. Migration nova só com as credenciais do migrador: cria tabela e altera tabela antiga.
            var nova = Flyway.configure().dataSource(urlDe(banco), migrador, "senha-migrador")
                    .locations("classpath:db/migration", "filesystem:" + novaMigration.toAbsolutePath()).load().migrate();
            assertThat(nova.success).isTrue();
            assertThat(nova.migrationsExecuted).isEqualTo(1);

            // 4. A API (app): dados sim, schema não.
            try (Connection c = DriverManager.getConnection(urlDe(banco), app, "senha-app"); Statement st = c.createStatement()) {
                st.execute("INSERT INTO cliente (id, tipo, documento, nome) VALUES (gen_random_uuid(), 'PJ', 'd-" + sufixo + "', 'x-" + sufixo + "')");
                try (var rs = st.executeQuery("SELECT count(*) FROM cliente WHERE nome='x-" + sufixo + "'")) {
                    rs.next();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
                st.execute("UPDATE cliente SET observacao_t16='ok' WHERE nome='x-" + sufixo + "'");
                st.execute("INSERT INTO nova_tabela (id, nome) VALUES (gen_random_uuid(), 'tabela criada pelo migrador')");
                try (var rs = st.executeQuery("SELECT count(*) FROM nova_tabela")) {
                    rs.next();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
                st.execute("DELETE FROM nova_tabela");
                st.execute("DELETE FROM cliente WHERE nome='x-" + sufixo + "'");

                assertThatThrownBy(() -> st.execute("CREATE TABLE intruso(id int)")).hasMessageContaining("permission denied");
                assertThatThrownBy(() -> st.execute("ALTER TABLE cliente ADD COLUMN x int")).hasMessageContaining("must be owner");
                assertThatThrownBy(() -> st.execute("DROP TABLE nova_tabela")).hasMessageContaining("must be owner");
                assertThatThrownBy(() -> st.execute("TRUNCATE cliente")).hasMessageContaining("permission denied");
                assertThatThrownBy(() -> st.execute("DROP TABLE flyway_schema_history")).hasMessageContaining("must be owner");
                assertThatThrownBy(() -> st.execute("DELETE FROM flyway_schema_history")).hasMessageContaining("permission denied");
            }
        } finally {
            admin("postgres", "DROP DATABASE IF EXISTS " + banco + " WITH (FORCE)");
            admin("postgres", "DROP ROLE IF EXISTS " + migrador);
            admin("postgres", "DROP ROLE IF EXISTS " + app);
        }
    }
}
