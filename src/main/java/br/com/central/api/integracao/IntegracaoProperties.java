package br.com.central.api.integracao;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "central.integracao")
public record IntegracaoProperties(
        String chavesEntrada,
        String chaveSaidaId,
        String chaveSaidaSegredo
) {
    /** {@code id:segredoBase64} separado por vírgula. Vazio = nenhuma chave aceita. */
    public Map<String, byte[]> chaves() {
        Map<String, byte[]> mapa = new LinkedHashMap<>();
        if (chavesEntrada == null || chavesEntrada.isBlank()) {
            return mapa;
        }
        for (String item : chavesEntrada.split(",")) {
            String[] partes = item.trim().split(":", 3);
            if (partes.length >= 2 && !partes[0].isBlank() && !partes[1].isBlank()) {
                mapa.put(partes[0].trim(), Base64.getDecoder().decode(partes[1].trim()));
            }
        }
        return mapa;
    }
    /** id:segredoBase64:produto; legado desta configuração Servirea fica restrito a SERVIREA. */
    public Map<String,String> produtos() {
        Map<String,String> mapa=new LinkedHashMap<>();
        if(chavesEntrada==null || chavesEntrada.isBlank())return mapa;
        for(String item:chavesEntrada.split(",")) {
            String[] partes=item.trim().split(":",3);
            if(partes.length<2 || partes[0].isBlank() || partes[1].isBlank())throw new IllegalArgumentException("Credencial de integração inválida.");
            String produto=partes.length==3 ? partes[2].trim() : "SERVIREA";
            if(produto.isBlank() || produto.length()>100 || !produto.matches("[A-Za-z0-9_-]+"))throw new IllegalArgumentException("Produto da credencial inválido.");
            if(mapa.putIfAbsent(partes[0].trim(),produto)!=null)throw new IllegalArgumentException("Identificador de credencial duplicado.");
        }
        return Map.copyOf(mapa);
    }
    @Override public String toString(){return "IntegracaoProperties[credenciais=ocultas]";}

}
