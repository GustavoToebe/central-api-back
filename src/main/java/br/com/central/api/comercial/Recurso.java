package br.com.central.api.comercial;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "recurso")
public class Recurso {

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoRecurso tipo;

    @Column(length = 40)
    private String unidade;

    protected Recurso() {
    }

    public Recurso(Produto produto, String codigo, String nome, TipoRecurso tipo) {
        this.produto = produto;
        this.codigo = codigo;
        this.nome = nome;
        this.tipo = tipo;
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

    public TipoRecurso getTipo() {
        return tipo;
    }

    public void setTipo(TipoRecurso tipo) {
        this.tipo = tipo;
    }

    public String getUnidade() {
        return unidade;
    }

    public void setUnidade(String unidade) {
        this.unidade = unidade;
    }
}
