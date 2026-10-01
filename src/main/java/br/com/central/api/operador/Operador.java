package br.com.central.api.operador;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "operador")
public class Operador {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String email;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "mfa_segredo") private String mfaSegredo;
    @Column(name = "mfa_pendente") private String mfaPendente;
    @Column(name = "mfa_pendente_ate") private Instant mfaPendenteAte;
    @Column(name = "mfa_ultimo_passo", nullable = false) private long mfaUltimoPasso = -1;
    @Column(name = "credenciais_versao", nullable = false) private long credenciaisVersao;

    public String getMfaSegredo() {return mfaSegredo;}
    public String getMfaPendente() {return mfaPendente;}
    public Instant getMfaPendenteAte() {return mfaPendenteAte;}
    public long getMfaUltimoPasso() {return mfaUltimoPasso;}
    public long getCredenciaisVersao() {return credenciaisVersao;}
    public void prepararMfa(String segredo, Instant ate) {mfaPendente = segredo; mfaPendenteAte = ate;}
    public void consumirPassoMfa(long passo) {mfaUltimoPasso = passo;}
    public void ativarMfa(long passo) {
        mfaSegredo = mfaPendente; mfaPendente = null; mfaPendenteAte = null;
        mfaUltimoPasso = passo; credenciaisVersao++;
    }
    public void desativarMfa() {
        mfaSegredo = null; mfaPendente = null; mfaPendenteAte = null;
        mfaUltimoPasso = -1; credenciaisVersao++;
    }

    protected Operador() {
    }

    public Operador(String nome, String email, String senhaHash) {
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
    }

    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void trocarSenha(String novoHash) {
        this.senhaHash = novoHash;
    }
}
