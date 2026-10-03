package br.com.central.api.operador;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.util.Locale;

/** Emula o autenticador somente nos testes HTTP; não expõe a API interna da biblioteca. */
final class CodigoTotpTeste {
    private CodigoTotpTeste() {}
    static String codigo(String segredo, long passo, int digitos) {
        try {
            byte[] chave = new byte[segredo.length() * 5 / 8];
            int acumulado = 0, bits = 0, posicao = 0;
            for (char c : segredo.toCharArray()) {
                int valor = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(c);
                if (valor < 0) throw new IllegalArgumentException("Segredo de teste inválido.");
                acumulado = acumulado << 5 | valor; bits += 5;
                if (bits >= 8) { bits -= 8; chave[posicao++] = (byte)(acumulado >>> bits); }
            }
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(chave, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(passo).array());
            int deslocamento = hash[hash.length - 1] & 15;
            int valor = ByteBuffer.wrap(hash, deslocamento, 4).getInt() & Integer.MAX_VALUE;
            return String.format(Locale.ROOT, "%0" + digitos + "d", valor % (digitos == 8 ? 100_000_000 : 1_000_000));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Não foi possível emular o autenticador no teste.", ex);
        }
    }
}
