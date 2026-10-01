package br.com.central.api.financeiro;
import jakarta.persistence.*;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity @Table(name="financeiro_conta")
public class ContaFinanceira {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(nullable=false, length=120) private String nome;
    @Column(nullable=false) private boolean ativo = true;
    protected ContaFinanceira() { }
    public ContaFinanceira(String nome) { this.nome = nome; }
    public UUID getId() { return id; }
    public String getNome() { return nome; }
    public boolean isAtivo() { return ativo; }
    public void setNome(String nome) { this.nome = nome; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    @Column(name="saldo_inicial", nullable=false, precision=14, scale=2)
    private BigDecimal saldoInicial = BigDecimal.ZERO;
    @Column(name="data_saldo_inicial", nullable=false)
    private LocalDate dataSaldoInicial;
    public BigDecimal getSaldoInicial() { return saldoInicial; }
    public LocalDate getDataSaldoInicial() { return dataSaldoInicial; }
    public void setSaldoInicial(BigDecimal valor) { saldoInicial = valor; }
    public void setDataSaldoInicial(LocalDate data) { dataSaldoInicial = data; }

}
