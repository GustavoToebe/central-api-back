package br.com.central.api.comercial;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Um período cobrado. O pagamento fica na própria linha (billing manual
 * do Servire). "Vencida" não é status: é ABERTA com vencimento anterior a hoje.
 */
@Entity
@Table(name = "cobranca")
public class Cobranca {

    public enum Status {
        ABERTA,
        PAGA,
        CANCELADA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

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

    protected Cobranca() {
    }

    public Cobranca(Contratacao contratacao, LocalDate competenciaInicio, LocalDate competenciaFim,
                    LocalDate vencimento) {
        this.contratacaoId = Objects.requireNonNull(contratacao.getId(), "contratação precisa estar salva");
        this.valor = contratacao.getValor();
        this.competenciaInicio = competenciaInicio;
        this.competenciaFim = competenciaFim;
        this.vencimento = vencimento;
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

    public void atualizarValor(BigDecimal valor) {
        this.valor = valor;
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
}
