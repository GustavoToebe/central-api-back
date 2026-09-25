package br.com.central.api.operador;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operador_id")
    private Operador operador;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "revogado_em")
    private Instant revogadoEm;

    @Column(name = "substituido_por")
    private UUID substituidoPor;

    private String ip;

    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private Instant criadoEm;

    protected RefreshToken() {
    }

    public RefreshToken(Operador operador, String tokenHash, Instant expiraEm, String ip) {
        this.operador = operador;
        this.tokenHash = tokenHash;
        this.expiraEm = expiraEm;
        this.ip = ip;
    }

    public UUID getId() {
        return id;
    }

    public Operador getOperador() {
        return operador;
    }

    public boolean isRevogado() {
        return revogadoEm != null;
    }

    public boolean isExpirado(Instant agora) {
        return !expiraEm.isAfter(agora);
    }

    public void revogar(Instant quando) {
        this.revogadoEm = quando;
    }

    public void setSubstituidoPor(UUID substituidoPor) {
        this.substituidoPor = substituidoPor;
    }
}
