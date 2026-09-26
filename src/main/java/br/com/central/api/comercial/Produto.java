package br.com.central.api.comercial;

import jakarta.persistence.Column;
import org.hibernate.annotations.Generated;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "produto")
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Número curto para o operador ditar e copiar (V007); o banco numera no insert. */
    @Generated
    @Column(insertable = false, updatable = false)
    private Long sequencial;

    @Column(nullable = false, length = 40)
    private String codigo;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(name = "url_base_integracao", length = 300)
    private String urlBaseIntegracao;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private Instant criadoEm;

    protected Produto() {
    }

    public Produto(String codigo, String nome) {
        this.codigo = codigo;
        this.nome = nome;
    }

    public UUID getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getUrlBaseIntegracao() {
        return urlBaseIntegracao;
    }

    public void setUrlBaseIntegracao(String urlBaseIntegracao) {
        this.urlBaseIntegracao = urlBaseIntegracao;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }
    public Long getSequencial() {
        return sequencial;
    }
}
