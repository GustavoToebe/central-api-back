package br.com.central.api.comercial;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "historico_contratacao")
public class HistoricoContratacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "contratacao_id", nullable = false)
    private UUID contratacaoId;

    @Column(name = "operador_id")
    private UUID operadorId;

    @Column(nullable = false, length = 40)
    private String acao;

    private String motivo;

    @Column(name = "versao_direitos", nullable = false)
    private int versaoDireitos;

    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private Instant criadoEm;

    protected HistoricoContratacao() {
    }

    public HistoricoContratacao(UUID contratacaoId, UUID operadorId, String acao, String motivo, int versaoDireitos) {
        this.contratacaoId = contratacaoId;
        this.operadorId = operadorId;
        this.acao = acao;
        this.motivo = motivo;
        this.versaoDireitos = versaoDireitos;
    }

    public UUID getId() {
        return id;
    }

    public UUID getContratacaoId() {
        return contratacaoId;
    }

    public UUID getOperadorId() {
        return operadorId;
    }

    public String getAcao() {
        return acao;
    }

    public String getMotivo() {
        return motivo;
    }

    public int getVersaoDireitos() {
        return versaoDireitos;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
