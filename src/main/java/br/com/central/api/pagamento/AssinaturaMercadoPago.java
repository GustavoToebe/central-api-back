package br.com.central.api.pagamento;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Manifesto oficial x-signature; entrada estrita para o tópico payment. Sem acesso a banco ou rede. */
public final class AssinaturaMercadoPago {
    private AssinaturaMercadoPago() {}
    public static boolean valida(String assinatura,String requestId,String pagamentoId,String segredo) {
        if (segredo==null || segredo.isBlank() || assinatura==null || assinatura.length()>256
            || requestId==null || !requestId.matches("[a-zA-Z0-9_-]{1,120}")
            || pagamentoId==null || !pagamentoId.matches("[0-9]{1,30}")) return false;
        String ts=null,hash=null;
        for (String parte:assinatura.split(",")) {
            String[] kv=parte.trim().split("=",2);
            if (kv.length!=2) return false;
            if (kv[0].equals("ts")) { if (ts!=null) return false; ts=kv[1]; }
            else if (kv[0].equals("v1")) { if (hash!=null) return false; hash=kv[1]; }
            else return false;
        }
        if (ts==null || !ts.matches("[0-9]{1,16}") || hash==null || !hash.matches("[a-fA-F0-9]{64}")) return false;
        try {
            String manifesto="id:"+pagamentoId+";request-id:"+requestId+";ts:"+ts+";";
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return MessageDigest.isEqual(mac.doFinal(manifesto.getBytes(StandardCharsets.UTF_8)),HexFormat.of().parseHex(hash));
        } catch (Exception ex) { return false; }
    }
}
