package br.com.central.api.operador;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

/** Chaves próprias, fora do banco; AAD impede transportar um segredo entre operadores. */
@Component
public class MfaCifra {
    private final Map<String, SecretKeySpec> chaves = new HashMap<>();
    private final String ativa;
    private final SecureRandom random = new SecureRandom();
    public MfaCifra(@Value("${CENTRAL_MFA_CHAVES:}") String configuracao,
                    @Value("${CENTRAL_MFA_CHAVE_ATIVA:}") String ativa) {
        this.ativa = ativa;
        if (!configuracao.isBlank()) for (String item : configuracao.split(",", -1)) {
            String[] partes = item.trim().split(":", -1);
            try {
                if (partes.length != 2 || !partes[0].matches("[A-Za-z0-9_-]{1,40}")) throw new IllegalArgumentException();
                byte[] key = Base64.getDecoder().decode(partes[1]);
                if (key.length != 32 || chaves.putIfAbsent(partes[0], new SecretKeySpec(key, "AES")) != null) throw new IllegalArgumentException();
            } catch (IllegalArgumentException ex) {throw new IllegalStateException("Configuração de chaves MFA inválida.");}
        }
        if ((!ativa.isBlank() || !chaves.isEmpty()) && !chaves.containsKey(ativa)) throw new IllegalStateException("Chave ativa MFA ausente.");
    }
    public boolean configurada() {return chaves.containsKey(ativa);}
    public String cifrar(UUID operador, String segredo) {
        if (!configurada()) throw new MfaException(HttpStatus.SERVICE_UNAVAILABLE, "MFA ainda não foi configurado no servidor.", "MFA_INDISPONIVEL");
        try {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, chaves.get(ativa), nonce, operador);
            return "v1:" + ativa + ":" + Base64.getEncoder().encodeToString(nonce) + ":" + Base64.getEncoder().encodeToString(cipher.doFinal(segredo.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.GeneralSecurityException ex) {throw indisponivel();}
    }
    public String decifrar(UUID operador, String valor) {
        try {
            String[] partes = valor.split(":", -1);
            if (partes.length != 4 || !partes[0].equals("v1") || !chaves.containsKey(partes[1])) throw new IllegalArgumentException();
            byte[] nonce = Base64.getDecoder().decode(partes[2]);
            if (nonce.length != 12) throw new IllegalArgumentException();
            return new String(cipher(Cipher.DECRYPT_MODE, chaves.get(partes[1]), nonce, operador).doFinal(Base64.getDecoder().decode(partes[3])), StandardCharsets.US_ASCII);
        } catch (java.security.GeneralSecurityException | IllegalArgumentException ex) {throw indisponivel();}
    }
    private Cipher cipher(int modo, SecretKeySpec key, byte[] nonce, UUID operador) throws java.security.GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(modo, key, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(("central:mfa:" + operador).getBytes(StandardCharsets.UTF_8)); return cipher;
    }
    private MfaException indisponivel() {return new MfaException(HttpStatus.SERVICE_UNAVAILABLE, "Não foi possível validar o segundo fator. Contate o responsável pela Central.", "MFA_INDISPONIVEL");}
}
