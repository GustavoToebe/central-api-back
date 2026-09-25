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
@Table(name = "contratacao_adicional")
public class ContratacaoAdicional {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contratacao_id", nullable = false)
    private Contratacao contratacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "adicional_id", nullable = false)
    private Adicional adicional;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal quantidade;

    protected ContratacaoAdicional() {
    }

    public ContratacaoAdicional(Contratacao contratacao, Adicional adicional, BigDecimal quantidade) {
        this.contratacao = contratacao;
        this.adicional = adicional;
        this.quantidade = quantidade;
    }

    public UUID getId() {
        return id;
    }

    public Contratacao getContratacao() {
        return contratacao;
    }

    public Adicional getAdicional() {
        return adicional;
    }

    public BigDecimal getQuantidade() {
        return quantidade;
    }
}
