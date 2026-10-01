package br.com.central.api.operador;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;

/** Contador por IP e conta antes do BCrypt; limites de memória e janela por instância. */
@Component
public class LimiteLogin {
    private record Janela(long ate,int tentativas) {}
    private final Map<String,Janela> janelas=new HashMap<>();
    private final int porIp,porConta,maxChaves;
    private final long janelaMs;
    private final Clock clock;
    private final String sal=UUID.randomUUID().toString();
    private long proximaLimpeza;
    @Autowired
    public LimiteLogin(@Value("${central.auth.limite-ip:60}") int porIp,
        @Value("${central.auth.limite-conta:10}") int porConta,
        @Value("${central.auth.janela-segundos:600}") long segundos,
        @Value("${central.auth.max-chaves:20000}") int maxChaves) {
        this(porIp,porConta,segundos,maxChaves,Clock.systemUTC());
    }
    LimiteLogin(int porIp,int porConta,long segundos,int maxChaves,Clock clock) {
        if(porIp<1 || porConta<1 || segundos<1 || segundos>86400 || maxChaves<2)throw new IllegalArgumentException("Limites de login inválidos.");
        this.porIp=porIp;this.porConta=porConta;this.maxChaves=maxChaves;this.janelaMs=segundos*1000;this.clock=clock;
    }
    public synchronized void registrar(String ip,String email) {
        long agora=clock.millis();
        if(agora>=proximaLimpeza){janelas.entrySet().removeIf(e -> e.getValue().ate()<=agora);proximaLimpeza=agora+Math.min(janelaMs,60000);}
        String endereco=chave("ip",ip==null ? "desconhecido" : ip);
        String conta=chave("conta",email==null ? "" : email.trim().toLowerCase(Locale.ROOT));
        Janela a=atual(endereco,agora),b=atual(conta,agora);
        if(a.tentativas()>=porIp || b.tentativas()>=porConta) {
            long ate=Math.max(a.tentativas()>=porIp ? a.ate() : agora,b.tentativas()>=porConta ? b.ate() : agora);
            throw new LimiteLoginException(Math.max(1,(ate-agora+999)/1000));
        }
        int novas=(janelas.containsKey(endereco)?0:1)+(janelas.containsKey(conta)?0:1);
        if(janelas.size()+novas>maxChaves)throw new LimiteLoginException(Math.max(1,(proximaLimpeza-agora+999)/1000));
        janelas.put(endereco,new Janela(a.ate(),a.tentativas()+1));janelas.put(conta,new Janela(b.ate(),b.tentativas()+1));
    }
    private Janela atual(String chave,long agora) {
        Janela janela=janelas.get(chave);
        return janela==null || janela.ate()<=agora ? new Janela(agora+janelaMs,0) : janela;
    }
    private String chave(String tipo,String valor) {
        try {return tipo+":"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((sal+":"+valor).getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException("SHA-256 indisponível.",e);}
    }
}
