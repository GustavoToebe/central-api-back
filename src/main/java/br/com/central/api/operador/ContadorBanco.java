package br.com.central.api.operador;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Contador no banco (tabela global login_tentativa, V082): vale entre réplicas e sobrevive a reinício. Uma instrução só conta e
 * lê, então tentativas concorrentes não passam do limite. A chave já chega como hash; nenhum e-mail nem IP é gravado em claro.
 */
@Component
class ContadorBanco implements ContadorJanelas {
    private static final String SQL = """
            insert into login_tentativa(chave, janela_ate, tentativas) values (?, ?, 1)
            on conflict (chave) do update set
              tentativas = case when login_tentativa.janela_ate <= ? then 1 else login_tentativa.tentativas + 1 end,
              janela_ate = case when login_tentativa.janela_ate <= ? then ? else login_tentativa.janela_ate end
            returning tentativas, janela_ate
            """;

    private final JdbcTemplate jdbc;
    private final AtomicLong proximaLimpeza = new AtomicLong();

    ContadorBanco(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Estado tentar(String chave, long agoraMs, long janelaMs) {
        limparSeNecessario(agoraMs);
        Timestamp agora = Timestamp.from(Instant.ofEpochMilli(agoraMs));
        Timestamp fim = Timestamp.from(Instant.ofEpochMilli(agoraMs + janelaMs));
        return jdbc.queryForObject(SQL, (rs, n) -> new Estado(rs.getInt(1), rs.getTimestamp(2).getTime()),
                chave, fim, agora, agora, fim);
    }

    /** Remove janelas vencidas há mais de uma hora, no máximo uma vez por minuto por processo. */
    private void limparSeNecessario(long agoraMs) {
        long previsto = proximaLimpeza.get();
        if (agoraMs >= previsto && proximaLimpeza.compareAndSet(previsto, agoraMs + 60000)) {
            jdbc.update("delete from login_tentativa where janela_ate < ?", Timestamp.from(Instant.ofEpochMilli(agoraMs - 3600000)));
        }
    }
}
