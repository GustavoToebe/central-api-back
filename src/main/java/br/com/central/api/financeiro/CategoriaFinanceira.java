package br.com.central.api.financeiro;
import jakarta.persistence.*;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity @Table(name="financeiro_categoria")
public class CategoriaFinanceira {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(nullable=false, length=120) private String nome;
    @Column(nullable=false) private boolean ativo = true;
    /** Entrada ou saída. O grupo define o tipo e as contas contábeis dele herdam o mesmo. */
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private MovimentoFinanceiro.Tipo tipo = MovimentoFinanceiro.Tipo.DESPESA;
    /** Vazio = grupo (só organiza); preenchido = conta contábil (recebe lançamentos). */
    @Column(name="grupo_id") private UUID grupoId;
    protected CategoriaFinanceira() { }
    public CategoriaFinanceira(String nome, MovimentoFinanceiro.Tipo tipo, UUID grupoId) { this.nome = nome; this.tipo = tipo; this.grupoId = grupoId; }
    public boolean isGrupo() { return grupoId == null; }
    public MovimentoFinanceiro.Tipo getTipo() { return tipo; }
    public UUID getGrupoId() { return grupoId; }
    public void setTipo(MovimentoFinanceiro.Tipo tipo) { this.tipo = tipo; }
    public void setGrupoId(UUID grupoId) { this.grupoId = grupoId; }
    public UUID getId() { return id; }
    public String getNome() { return nome; }
    public boolean isAtivo() { return ativo; }
    public void setNome(String nome) { this.nome = nome; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

}
