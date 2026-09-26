package br.com.central.api.integracao;

import br.com.central.api.comercial.Contratacao;
import br.com.central.api.comercial.Produto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Erro de servidor relatado por um aplicativo (contrato 6.2, V006). Só
 * leitura aqui: a gravação é um {@code INSERT ... ON CONFLICT} no repositório,
 * porque o id vem do app e o reenvio do mesmo lote não pode duplicar.
 */
@Entity
@Table(name = "erro_aplicativo")
public class ErroAplicativo {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false, insertable = false, updatable = false)
    private Produto produto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contratacao_id", insertable = false, updatable = false)
    private Contratacao contratacao;

    @Column(name = "tenant_id", insertable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "usuario_id", insertable = false, updatable = false)
    private UUID usuarioId;

    @Column(name = "ocorrido_em", insertable = false, updatable = false)
    private Instant ocorridoEm;

    @Column(name = "recebido_em", insertable = false, updatable = false)
    private Instant recebidoEm;

    @Column(insertable = false, updatable = false)
    private String metodo;

    @Column(insertable = false, updatable = false)
    private String rota;

    @Column(insertable = false, updatable = false)
    private int status;

    @Column(insertable = false, updatable = false)
    private String codigo;

    @Column(insertable = false, updatable = false)
    private String mensagem;

    @Column(name = "request_id", insertable = false, updatable = false)
    private String requestId;

    protected ErroAplicativo() {
    }

    public UUID getId() {
        return id;
    }

    public Produto getProduto() {
        return produto;
    }

    public Contratacao getContratacao() {
        return contratacao;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public Instant getOcorridoEm() {
        return ocorridoEm;
    }

    public Instant getRecebidoEm() {
        return recebidoEm;
    }

    public String getMetodo() {
        return metodo;
    }

    public String getRota() {
        return rota;
    }

    public int getStatus() {
        return status;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getMensagem() {
        return mensagem;
    }

    public String getRequestId() {
        return requestId;
    }
}
