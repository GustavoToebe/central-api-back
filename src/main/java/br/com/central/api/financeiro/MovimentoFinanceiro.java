package br.com.central.api.financeiro;
import jakarta.persistence.*;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;

/** Lançamento simples. A baixa é manual e nunca cria cobrança da assinatura do SaaS. */
@Entity @Table(name="financeiro_movimento")
public class MovimentoFinanceiro {
    public enum Tipo { RECEITA, DESPESA }
    public enum Situacao { PENDENTE, PAGO, CANCELADO }
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Version private long versao;
    @Column(nullable=false, length=200) private String descricao;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private Tipo tipo;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private Situacao situacao = Situacao.PENDENTE;
    @Column(nullable=false, precision=14, scale=2) private BigDecimal valor;
    @Column(nullable=false) private LocalDate vencimento;
    @Column(name="data_pagamento") private LocalDate dataPagamento;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="conta_id", nullable=false) private ContaFinanceira conta;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="categoria_id", nullable=false) private CategoriaFinanceira categoria;
    @Column(length=1000) private String observacoes;
    @Column(name="criado_em", nullable=false, updatable=false) private Instant criadoEm = Instant.now();
    protected MovimentoFinanceiro() { }
    public MovimentoFinanceiro(String descricao) { this.descricao = descricao; }
    public UUID getId() { return id; }
    public long getVersao() { return versao; }
    public String getDescricao() { return descricao; }
    public Tipo getTipo() { return tipo; }
    public Situacao getSituacao() { return situacao; }
    public BigDecimal getValor() { return valor; }
    public LocalDate getVencimento() { return vencimento; }
    public LocalDate getDataPagamento() { return dataPagamento; }
    public ContaFinanceira getConta() { return conta; }
    public CategoriaFinanceira getCategoria() { return categoria; }
    public String getObservacoes() { return observacoes; }
    public void setDescricao(String v) { descricao=v; }
    public void setTipo(Tipo v) { tipo=v; }
    public void setSituacao(Situacao v) { situacao=v; }
    public void setValor(BigDecimal v) { valor=v; }
    public void setVencimento(LocalDate v) { vencimento=v; }
    public void setDataPagamento(LocalDate v) { dataPagamento=v; }
    public void setConta(ContaFinanceira v) { conta=v; }
    public void setCategoria(CategoriaFinanceira v) { categoria=v; }
    public void setObservacoes(String v) { observacoes=v; }
}
