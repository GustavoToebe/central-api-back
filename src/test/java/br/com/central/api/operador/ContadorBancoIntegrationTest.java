package br.com.central.api.operador;

import br.com.central.api.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContadorBancoIntegrationTest extends AbstractIntegrationTest {
    @Autowired ContadorBanco contador;
    @Autowired JdbcTemplate jdbc;

    LimiteLogin limite(int ip, int conta) {
        return new LimiteLogin(ip, conta, 60, "segredo", contador);
    }

    @Test
    void duasInstanciasCompartilhamOMesmoLimiteEOBancoNaoGuardaEmailNemIpEmClaro() {
        String email = "ana-" + UUID.randomUUID() + "@teste.com";
        var a = limite(100, 2);
        var b = limite(100, 2); // outra réplica
        a.registrar("1.1.1.1", email);
        b.registrar("2.2.2.2", email.toUpperCase());
        assertThatThrownBy(() -> a.registrar("3.3.3.3", email)).isInstanceOf(LimiteLoginException.class);
        var chaves = jdbc.queryForList("select chave from login_tentativa", String.class);
        assertThat(chaves).isNotEmpty().allSatisfy(c -> assertThat(c).doesNotContain("@").doesNotContain("1.1.1.1"));
    }

    @Test
    void concorrenciaNaoUltrapassaOLimiteDoMesmoIp() throws Exception {
        String ip = "ip-" + UUID.randomUUID();
        var l = limite(5, 100);
        var aceitas = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(8)) {
            var tarefas = new java.util.ArrayList<Future<?>>();
            for (int n = 0; n < 30; n++) {
                int i = n;
                tarefas.add(pool.submit(() -> {
                    try {
                        l.registrar(ip, "c" + i + "-" + UUID.randomUUID() + "@teste.com");
                        aceitas.incrementAndGet();
                    } catch (LimiteLoginException esperado) {
                        // acima do limite
                    }
                }));
            }
            for (var f : tarefas) {
                f.get(15, TimeUnit.SECONDS);
            }
        }
        assertThat(aceitas.get()).isEqualTo(5);
    }

    @Test
    void janelaVencidaRecomeca() {
        String chave = "teste:" + UUID.randomUUID();
        long t0 = System.currentTimeMillis();
        assertThat(contador.tentar(chave, t0, 1000).tentativas()).isEqualTo(1);
        assertThat(contador.tentar(chave, t0 + 500, 1000).tentativas()).isEqualTo(2);
        assertThat(contador.tentar(chave, t0 + 1500, 1000).tentativas()).isEqualTo(1);
    }
}
