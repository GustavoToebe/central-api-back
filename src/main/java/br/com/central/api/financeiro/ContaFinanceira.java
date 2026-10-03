package br.com.central.api.financeiro;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.ArrayList;
import java.util.List;
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

    public enum TipoConta { CORRENTE, POUPANCA, CAIXA, OUTRA }
    @Enumerated(EnumType.STRING) @Column(name="tipo_conta", nullable=false, length=20)
    private TipoConta tipoConta = TipoConta.OUTRA;
    @Column(length=120) private String banco;
    @Column(length=20) private String agencia;
    @Column(name="numero_conta", length=30) private String numeroConta;
    @Column(length=120) private String titular;
    @Column(name="data_abertura") private LocalDate dataAbertura;
    @Column(name="data_encerramento") private LocalDate dataEncerramento;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="chaves_pix", nullable=false)
    private List<ChavePixDto> chavesPix = new ArrayList<>();
    public TipoConta getTipoConta() { return tipoConta; }
    public String getBanco() { return banco; }
    public String getAgencia() { return agencia; }
    public String getNumeroConta() { return numeroConta; }
    public String getTitular() { return titular; }
    public LocalDate getDataAbertura() { return dataAbertura; }
    public LocalDate getDataEncerramento() { return dataEncerramento; }
    public List<ChavePixDto> getChavesPix() { return chavesPix; }
    public void setDadosBancarios(TipoConta tipo, String banco, String agencia, String numero, String titular, LocalDate abertura, LocalDate encerramento, List<ChavePixDto> chaves) {
        this.tipoConta = tipo; this.banco = banco; this.agencia = agencia; this.numeroConta = numero; this.titular = titular;
        this.dataAbertura = abertura; this.dataEncerramento = encerramento; this.chavesPix = new ArrayList<>(chaves);
    }

}
