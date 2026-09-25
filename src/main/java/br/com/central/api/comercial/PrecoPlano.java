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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "preco_plano")
public class PrecoPlano {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "plano_id", nullable = false)
    private UUID planoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Periodicidade periodicidade;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private Instant criadoEm;

    protected PrecoPlano() {
    }

    public PrecoPlano(UUID planoId, Periodicidade periodicidade, BigDecimal valor, LocalDate vigenteDesde) {
        this.planoId = planoId;
        this.periodicidade = periodicidade;
        this.valor = valor;
        this.vigenteDesde = vigenteDesde;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPlanoId() {
        return planoId;
    }

    public Periodicidade getPeriodicidade() {
        return periodicidade;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public LocalDate getVigenteDesde() {
        return vigenteDesde;
    }
}
