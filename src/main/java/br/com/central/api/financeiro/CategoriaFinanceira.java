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
    protected CategoriaFinanceira() { }
    public CategoriaFinanceira(String nome) { this.nome = nome; }
    public UUID getId() { return id; }
    public String getNome() { return nome; }
    public boolean isAtivo() { return ativo; }
    public void setNome(String nome) { this.nome = nome; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

}
