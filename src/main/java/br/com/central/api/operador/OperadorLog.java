package br.com.central.api.operador;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "operador_log")
public class OperadorLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "operador_id")
    private UUID operadorId;

    @Column(nullable = false)
    private String acao;

    private String detalhe;

    private String ip;

    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private Instant criadoEm;

    protected OperadorLog() {
    }

    public OperadorLog(UUID operadorId, String acao, String detalhe, String ip) {
        this.operadorId = operadorId;
        this.acao = acao;
        this.detalhe = detalhe;
        this.ip = ip;
    }
}
