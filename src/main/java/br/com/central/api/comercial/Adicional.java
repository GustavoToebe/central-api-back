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

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "adicional")
public class Adicional {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recurso_id", nullable = false)
    private Recurso recurso;

    @Column(nullable = false, length = 60)
    private String codigo;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal quantidade;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    @Column(nullable = false)
    private boolean ativo = true;

    protected Adicional() {
    }

    public Adicional(Produto produto, Recurso recurso, String codigo, String nome,
                     BigDecimal quantidade, BigDecimal preco) {
        this.produto = produto;
        this.recurso = recurso;
        this.codigo = codigo;
        this.nome = nome;
        this.quantidade = quantidade;
        this.preco = preco;
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

    public Recurso getRecurso() {
        return recurso;
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

    public BigDecimal getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(BigDecimal quantidade) {
        this.quantidade = quantidade;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public void setPreco(BigDecimal preco) {
        this.preco = preco;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }
}
