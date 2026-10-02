package br.com.central.api.local;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** O seed de teste só pode rodar contra um Postgres desta máquina. */
class AmbienteLocalTest {

    @Test
    void snapshotDoSeedPodeSerLidoPelaIntegracaoSemPerderAcesso() {
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        var cliente = java.util.UUID.randomUUID();
        var serializado = json.writeValueAsString(AmbienteLocal.direitosDeExemplo(cliente));
        var direitos = json.readValue(serializado, br.com.central.api.comercial.dto.DireitosInstancia.class);
        assertThat(direitos.versao()).isEqualTo(1);
        assertThat(direitos.acessoLiberado()).isTrue();
        assertThat(direitos.clienteId()).isEqualTo(cliente);
        assertThat(direitos.tenantId()).isEqualTo(AmbienteLocal.TENANT_ID);
        assertThat(direitos.contratacaoId()).isEqualTo(AmbienteLocal.CONTRATACAO_ID);
        assertThat(direitos.limites()).isEmpty();
        assertThat(direitos.funcionalidades()).contains("ESCALAS","IMPORTACAO_PESSOAS","PORTAL_VOLUNTARIO").doesNotHaveDuplicates();
    }

    @Test
    void aceitaSoOsHostsDaPropriaMaquina() {
        assertThat(AmbienteLocal.bancoNestaMaquina("jdbc:postgresql://localhost:5433/servirea_dev")).isTrue();
        assertThat(AmbienteLocal.bancoNestaMaquina("jdbc:postgresql://LOCALHOST/db")).isTrue();
        assertThat(AmbienteLocal.bancoNestaMaquina("jdbc:postgresql://127.0.0.1:5432/db")).isTrue();
        assertThat(AmbienteLocal.bancoNestaMaquina("jdbc:postgresql://[::1]:5432/db")).isTrue();
    }

    @Test
    void recusaQualquerOutroBancoMesmoComLocalhostNoTexto() {
        assertThat(AmbienteLocal.bancoNestaMaquina("jdbc:postgresql://aws-0-ca-central-1.pooler.supabase.com:5432/postgres?sslmode=require")).isFalse();
        assertThat(AmbienteLocal.bancoNestaMaquina("jdbc:postgresql://aws-0.pooler.supabase.com/postgres?app=localhost")).isFalse();
        assertThat(AmbienteLocal.bancoNestaMaquina("jdbc:postgresql://localhost.exemplo.com/db")).isFalse();
        assertThat(AmbienteLocal.bancoNestaMaquina("jdbc:postgresql://db.exemplo.com/localhost")).isFalse();
        assertThat(AmbienteLocal.bancoNestaMaquina("jdbc:postgresql://localhost,db.exemplo.com/db")).isFalse();
    }

    @Test
    void recusaUrlVaziaNulaOuMalFormada() {
        assertThat(AmbienteLocal.bancoNestaMaquina(null)).isFalse();
        assertThat(AmbienteLocal.bancoNestaMaquina("")).isFalse();
        assertThat(AmbienteLocal.bancoNestaMaquina("localhost")).isFalse();
        assertThat(AmbienteLocal.bancoNestaMaquina("jdbc:postgresql://local host/db")).isFalse();
    }
}
