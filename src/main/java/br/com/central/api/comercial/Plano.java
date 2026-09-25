package br.com.central.api.comercial;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "plano")
public class Plano {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(nullable = false, length = 60)
    private String codigo;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private Instant criadoEm;

    protected Plano() {
    }

    public Plano(Produto produto, String codigo, String nome) {
        this.produto = produto;
        this.codigo = codigo;
        this.nome = nome;
    }

    public UUID getId() {
        return id;
    }

    public Produto getProduto() {
        return produto;
    }

    public UUID getProdutoId() {
        return produto.getId();
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

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }
}
