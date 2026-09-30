package br.com.central.api.comercial;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import org.hibernate.annotations.Generated;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Um período cobrado. O pagamento fica na própria linha (billing manual
 * do Servirea). "Vencida" não é status: é ABERTA com vencimento anterior a hoje.
 */
@Entity
@Table(name = "cobranca")
public class Cobranca {

    public enum Status {
        ABERTA,
        PAGA,
        CANCELADA,
        /** Não é cobrada: valor zero, isenção da contratação ou o botão "Isentar" (V010). Ocupa a competência. */
        ISENTA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Número curto para o operador ditar e copiar (V007); o banco numera no insert. */
    @Generated
    @Column(insertable = false, updatable = false)
    private Long sequencial;

    @Column(name = "contratacao_id", nullable = false, updatable = false)
    private UUID contratacaoId;

    @Column(name = "competencia_inicio", nullable = false)
    private LocalDate competenciaInicio;

    @Column(name = "competencia_fim", nullable = false)
    private LocalDate competenciaFim;

    @Column(nullable = false)
    private LocalDate vencimento;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ABERTA;

    @Column(name = "pago_em")
    private LocalDate pagoEm;

    @Column(name = "valor_pago", precision = 10, scale = 2)
    private BigDecimal valorPago;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", length = 20)
    private FormaPagamento formaPagamento;

    private String observacao;

    @Column(name = "registrado_por")
    private UUID registradoPor;

    /** Só leitura, para a lista geral filtrar por cliente e produto. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contratacao_id", insertable = false, updatable = false)
    private Contratacao contratacao;

    @OneToMany(mappedBy = "cobranca", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordem")
    private List<CobrancaItem> itens = new ArrayList<>();

    protected Cobranca() {
    }

    public Cobranca(Contratacao contratacao, LocalDate competenciaInicio, LocalDate competenciaFim,
                    LocalDate vencimento, List<CobrancaItem> itens) {
        this.contratacaoId = Objects.requireNonNull(contratacao.getId(), "contratação precisa estar salva");
        this.competenciaInicio = competenciaInicio;
        this.competenciaFim = competenciaFim;
        this.vencimento = vencimento;
        definirItens(itens);
    }

    /**
     * Troca os itens e refaz o valor pela soma deles. Só para cobrança em
     * aberto: a paga guarda o que foi cobrado de fato.
     */
    public void definirItens(List<CobrancaItem> novos) {
        if (status != Status.ABERTA) {
            throw new IllegalStateException("Só cobrança em aberto tem os itens refeitos.");
        }
        itens.clear();
        BigDecimal total = BigDecimal.ZERO;
        int ordem = 0;
        for (CobrancaItem item : novos) {
            item.vincular(this, ordem++);
            itens.add(item);
            total = total.add(item.getValor());
        }
        this.valor = total;
    }

    public void pagar(LocalDate pagoEm, BigDecimal valorPago, FormaPagamento forma, String observacao,
                      UUID registradoPor) {
        this.status = Status.PAGA;
        this.pagoEm = pagoEm;
        this.valorPago = valorPago;
        this.formaPagamento = forma;
        this.observacao = observacao;
        this.registradoPor = registradoPor;
    }

    public void estornar() {
        this.status = Status.ABERTA;
        this.pagoEm = null;
        this.valorPago = null;
        this.formaPagamento = null;
        this.registradoPor = null;
    }

    public void cancelar(String motivo) {
        this.status = Status.CANCELADA;
        if (motivo != null) {
            this.observacao = motivo;
        }
    }

    public void isentar(String motivo) {
        if (status != Status.ABERTA) {
            throw new IllegalStateException("Só cobrança em aberto fica isenta.");
        }
        this.status = Status.ISENTA;
        this.observacao = motivo;
    }

    /** Volta a isenta para em aberto (fim da isenção da contratação). */
    public void reabrirIsenta() {
        if (status != Status.ISENTA) {
            throw new IllegalStateException("Só cobrança isenta volta a ficar em aberto.");
        }
        this.status = Status.ABERTA;
        this.observacao = null;
    }

    public boolean vencidaEm(LocalDate hoje) {
        return status == Status.ABERTA && vencimento.isBefore(hoje);
    }

    public UUID getId() {
        return id;
    }

    public UUID getContratacaoId() {
        return contratacaoId;
    }

    public LocalDate getCompetenciaInicio() {
        return competenciaInicio;
    }

    public LocalDate getCompetenciaFim() {
        return competenciaFim;
    }

    public LocalDate getVencimento() {
        return vencimento;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public Status getStatus() {
        return status;
    }

    public LocalDate getPagoEm() {
        return pagoEm;
    }

    public BigDecimal getValorPago() {
        return valorPago;
    }

    public FormaPagamento getFormaPagamento() {
        return formaPagamento;
    }

    public String getObservacao() {
        return observacao;
    }

    public Contratacao getContratacao() {
        return contratacao;
    }

    public List<CobrancaItem> getItens() {
        return itens;
    }
    public Long getSequencial() {
        return sequencial;
    }
}
