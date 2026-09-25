package br.com.central.api.comercial;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "plano_recurso")
@IdClass(PlanoRecursoId.class)
public class PlanoRecurso {

    @Id
    @Column(name = "plano_id")
    private UUID planoId;

    @Id
    @Column(name = "recurso_id")
    private UUID recursoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recurso_id", insertable = false, updatable = false)
    private Recurso recurso;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    protected PlanoRecurso() {
    }

    public PlanoRecurso(UUID planoId, Recurso recurso, BigDecimal valor) {
        this.planoId = planoId;
        this.recursoId = recurso.getId();
        this.recurso = recurso;
        this.valor = valor;
    }

    public UUID getPlanoId() {
        return planoId;
    }

    public Recurso getRecurso() {
        return recurso;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }
}
