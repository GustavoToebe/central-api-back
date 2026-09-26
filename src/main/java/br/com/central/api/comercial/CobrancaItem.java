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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Uma linha da cobrança: o plano do período ou um adicional (V005, 26/09/2026).
 * É cópia do momento em que a cobrança foi gerada ou recalculada, para que
 * mudar o catálogo depois não reescreva o que já foi cobrado.
 */
@Entity
@Table(name = "cobranca_item")
public class CobrancaItem {

    public enum Tipo {
        PLANO,
        ADICIONAL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cobranca_id", nullable = false)
    private Cobranca cobranca;

    @Column(nullable = false)
    private int ordem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Tipo tipo;

    @Column(nullable = false, length = 300)
    private String descricao;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal quantidade;

    @Column(name = "valor_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorUnitario;

    @Column(nullable = false)
    private int meses;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    protected CobrancaItem() {
    }

    private CobrancaItem(Tipo tipo, String descricao, BigDecimal quantidade, BigDecimal valorUnitario, int meses,
                         BigDecimal valor) {
        this.tipo = tipo;
        this.descricao = descricao;
        this.quantidade = quantidade;
        this.valorUnitario = valorUnitario;
        this.meses = meses;
        this.valor = valor.setScale(2, RoundingMode.HALF_UP);
    }

    /** O valor da contratação já é o preço do período inteiro. */
    public static CobrancaItem plano(String descricao, BigDecimal valorDoPeriodo) {
        return new CobrancaItem(Tipo.PLANO, descricao, BigDecimal.ONE, valorDoPeriodo, 1, valorDoPeriodo);
    }

    /** O preço do adicional é mensal: entra quantidade × preço × meses do período. */
    public static CobrancaItem adicional(String descricao, BigDecimal quantidade, BigDecimal precoMensal, int meses) {
        return new CobrancaItem(Tipo.ADICIONAL, descricao, quantidade, precoMensal, meses,
                quantidade.multiply(precoMensal).multiply(BigDecimal.valueOf(meses)));
    }

    void vincular(Cobranca cobranca, int ordem) {
        this.cobranca = cobranca;
        this.ordem = ordem;
    }

    public UUID getId() {
        return id;
    }

    public int getOrdem() {
        return ordem;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public String getDescricao() {
        return descricao;
    }

    public BigDecimal getQuantidade() {
        return quantidade;
    }

    public BigDecimal getValorUnitario() {
        return valorUnitario;
    }

    public int getMeses() {
        return meses;
    }

    public BigDecimal getValor() {
        return valor;
    }
}
