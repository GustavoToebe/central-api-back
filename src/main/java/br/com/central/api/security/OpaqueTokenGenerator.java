package br.com.central.api.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Token opaco de 256 bits. O banco guarda só o SHA-256, para a busca por hash ser determinística. */
public final class OpaqueTokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private OpaqueTokenGenerator() {
    }

    public static String gerar() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String tokenBruto) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(tokenBruto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível na JVM", e);
        }
    }
}
