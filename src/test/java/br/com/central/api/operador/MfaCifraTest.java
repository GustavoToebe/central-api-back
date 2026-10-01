package br.com.central.api.operador;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class MfaCifraTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    @Test void nonceAleatorioAutenticacaoEAadPorOperador() {
        MfaCifra cifra = new MfaCifra("teste:" + KEY, "teste"); UUID id = UUID.randomUUID();
        String a = cifra.cifrar(id, "SEGREDO"), b = cifra.cifrar(id, "SEGREDO");
        assertThat(a).doesNotContain("SEGREDO").isNotEqualTo(b);
        assertThat(cifra.decifrar(id, a)).isEqualTo("SEGREDO");
        assertThatThrownBy(() -> cifra.decifrar(UUID.randomUUID(), a)).isInstanceOf(MfaException.class);
        String adulterado = a.substring(0, a.length() - 5) + "AAAAA";
        assertThatThrownBy(() -> cifra.decifrar(id, adulterado)).isInstanceOf(MfaException.class);
        assertThatThrownBy(() -> new MfaCifra("", "").decifrar(id, a)).isInstanceOf(MfaException.class);
    }
    @Test void configuracaoOpcionalNaoPermiteAtivarSemChave() {
        MfaCifra cifra = new MfaCifra("", ""); assertThat(cifra.configurada()).isFalse();
        assertThatThrownBy(() -> cifra.cifrar(UUID.randomUUID(), "X")).isInstanceOf(MfaException.class);
        assertThatThrownBy(() -> new MfaCifra("x:" + KEY + ",x:" + KEY, "x")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new MfaCifra("x:AA==", "x")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new MfaCifra("x:" + KEY, "ausente")).isInstanceOf(IllegalStateException.class);
    }
    @Test void leChaveAnteriorDuranteRotacao() {
        UUID id = UUID.randomUUID(); String valor = new MfaCifra("antiga:" + KEY, "antiga").cifrar(id, "SEGREDO");
        byte[] bytes = new byte[32]; Arrays.fill(bytes, (byte)7);
        MfaCifra nova = new MfaCifra("antiga:" + KEY + ",nova:" + Base64.getEncoder().encodeToString(bytes), "nova");
        assertThat(nova.decifrar(id, valor)).isEqualTo("SEGREDO");
        assertThat(nova.cifrar(id, "SEGREDO")).startsWith("v1:nova:");
    }
}
