package br.com.central.api.comercial;

import jakarta.persistence.Column;
import org.hibernate.annotations.Generated;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * O id é gerado na aplicação e copiado para {@code idempotency_key} na
 * criação; a chave nunca muda, porque o app exige Idempotency-Key igual ao
 * {@code contratacaoId} do corpo (26/09/2026). {@link Persistable} evita
 * que o Spring Data trate esse id preenchido como entidade já existente
 * (merge em vez de insert).
 */
@Entity
@Table(name = "contratacao")
public class Contratacao implements Persistable<UUID> {

    @Id
    private UUID id;

    /** Número curto para o operador ditar e copiar (V007); o banco numera no insert. */
    @Generated
    @Column(insertable = false, updatable = false)
    private Long sequencial;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plano_id", nullable = false)
    private Plano plano;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Periodicidade periodicidade;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(name = "dia_vencimento", nullable = false)
    private int diaVencimento;

    @Column(nullable = false)
    private LocalDate inicio;

    @Column(name = "vigente_ate")
    private LocalDate vigenteAte;

    @Enumerated(EnumType.STRING)
    @Column(name = "situacao_comercial", nullable = false, length = 20)
    private SituacaoComercial situacaoComercial;

    @Enumerated(EnumType.STRING)
    @Column(name = "situacao_provisionamento", nullable = false, length = 20)
    private SituacaoProvisionamento situacaoProvisionamento = SituacaoProvisionamento.PENDENTE;

    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    @Column(name = "id_externo")
    private UUID idExterno;

    /** Ver V003: NULL = nunca enviado; 0 = sem resposta; senão o status HTTP. */
    @Column(name = "ultimo_status_provisionamento")
    private Integer ultimoStatusProvisionamento;

    @Column(name = "nome_instancia", nullable = false, length = 200)
    private String nomeInstancia;

    @Column(name = "slug_instancia", nullable = false, length = 80)
    private String slugInstancia;

    @Column(name = "admin_nome", nullable = false, length = 200)
    private String adminNome;

    @Column(name = "admin_email", nullable = false, length = 200)
    private String adminEmail;

    @Column(name = "motivo_bloqueio")
    private String motivoBloqueio;

    /** Isenta de cobrança (V010): as cobranças das competências até {@link #isentaAte} nascem isentas. */
    @Column(nullable = false)
    private boolean isenta;

    @Column(name = "isencao_motivo")
    private String isencaoMotivo;

    /** Último dia da última competência isenta; vazio com {@link #isenta} = sem data para acabar. */
    @Column(name = "isenta_ate")
    private LocalDate isentaAte;

    @Column(name = "versao_direitos", nullable = false)
    private int versaoDireitos;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "direitos_atuais", nullable = false)
    private String direitosAtuais;

    private String observacoes;

    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private Instant criadoEm;

    @Transient
    private boolean nova = true;

    protected Contratacao() {
    }

    public Contratacao(Cliente cliente, Produto produto, Plano plano, Periodicidade periodicidade,
                       BigDecimal valor, int diaVencimento, LocalDate inicio,
                       SituacaoComercial situacaoComercial, String nomeInstancia, String slugInstancia,
                       String adminNome, String adminEmail) {
        this.id = UUID.randomUUID();
        this.idempotencyKey = this.id;
        this.cliente = cliente;
        this.produto = produto;
        this.plano = plano;
        this.periodicidade = periodicidade;
        this.valor = valor;
        this.diaVencimento = diaVencimento;
        this.inicio = inicio;
        this.situacaoComercial = situacaoComercial;
        this.nomeInstancia = nomeInstancia;
        this.slugInstancia = slugInstancia;
        this.adminNome = adminNome;
        this.adminEmail = adminEmail;
    }

    /**
     * Nome, slug e administrador só mudam enquanto nenhum envio pode ter
     * criado a instância no app: antes do primeiro POST, ou depois de uma
     * recusa 4xx que não seja 409 (o app recusa antes de gravar). Com 2xx,
     * 5xx, falha de rede ou 409 a instância pode existir; um corpo diferente
     * com a mesma Idempotency-Key voltaria 409 para sempre.
     */
    public boolean dadosDeProvisionamentoEditaveis() {
        if (idExterno != null) {
            return false;
        }
        Integer status = ultimoStatusProvisionamento;
        return status == null || (status >= 400 && status < 500 && status != 409);
    }

    public Integer getUltimoStatusProvisionamento() {
        return ultimoStatusProvisionamento;
    }

    public void setUltimoStatusProvisionamento(Integer ultimoStatusProvisionamento) {
        this.ultimoStatusProvisionamento = ultimoStatusProvisionamento;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return nova;
    }

    @PostPersist
    @PostLoad
    void jaPersistida() {
        nova = false;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public Produto getProduto() {
        return produto;
    }

    public Plano getPlano() {
        return plano;
    }

    public void setPlano(Plano plano) {
        this.plano = plano;
    }

    public Periodicidade getPeriodicidade() {
        return periodicidade;
    }

    public void setPeriodicidade(Periodicidade periodicidade) {
        this.periodicidade = periodicidade;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public int getDiaVencimento() {
        return diaVencimento;
    }

    public void setDiaVencimento(int diaVencimento) {
        this.diaVencimento = diaVencimento;
    }

    public LocalDate getInicio() {
        return inicio;
    }

    public LocalDate getVigenteAte() {
        return vigenteAte;
    }

    public void setVigenteAte(LocalDate vigenteAte) {
        this.vigenteAte = vigenteAte;
    }

    public SituacaoComercial getSituacaoComercial() {
        return situacaoComercial;
    }

    public void setSituacaoComercial(SituacaoComercial situacaoComercial) {
        this.situacaoComercial = situacaoComercial;
    }

    public SituacaoProvisionamento getSituacaoProvisionamento() {
        return situacaoProvisionamento;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getIdExterno() {
        return idExterno;
    }

    public void setIdExterno(UUID idExterno) {
        this.idExterno = idExterno;
    }

    public void setSituacaoProvisionamento(SituacaoProvisionamento situacaoProvisionamento) {
        this.situacaoProvisionamento = situacaoProvisionamento;
    }

    public String getNomeInstancia() {
        return nomeInstancia;
    }

    public void setNomeInstancia(String nomeInstancia) {
        this.nomeInstancia = nomeInstancia;
    }

    public String getSlugInstancia() {
        return slugInstancia;
    }

    public void setSlugInstancia(String slugInstancia) {
        this.slugInstancia = slugInstancia;
    }

    public String getAdminNome() {
        return adminNome;
    }

    public void setAdminNome(String adminNome) {
        this.adminNome = adminNome;
    }

    public String getAdminEmail() {
        return adminEmail;
    }

    public void setAdminEmail(String adminEmail) {
        this.adminEmail = adminEmail;
    }

    public String getMotivoBloqueio() {
        return motivoBloqueio;
    }

    public void setMotivoBloqueio(String motivoBloqueio) {
        this.motivoBloqueio = motivoBloqueio;
    }

    public void isentar(String motivo, LocalDate ate) {
        this.isenta = true;
        this.isencaoMotivo = motivo;
        this.isentaAte = ate;
    }

    public void encerrarIsencao() {
        this.isenta = false;
        this.isencaoMotivo = null;
        this.isentaAte = null;
    }

    /** A competência que começa em {@code inicio} está dentro da isenção. */
    public boolean isentaEm(LocalDate inicio) {
        return isenta && (isentaAte == null || !inicio.isAfter(isentaAte));
    }

    public boolean isIsenta() {
        return isenta;
    }

    public String getIsencaoMotivo() {
        return isencaoMotivo;
    }

    public LocalDate getIsentaAte() {
        return isentaAte;
    }

    public int getVersaoDireitos() {
        return versaoDireitos;
    }

    public void setVersaoDireitos(int versaoDireitos) {
        this.versaoDireitos = versaoDireitos;
    }

    public String getDireitosAtuais() {
        return direitosAtuais;
    }

    public void setDireitosAtuais(String direitosAtuais) {
        this.direitosAtuais = direitosAtuais;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }
    public Long getSequencial() {
        return sequencial;
    }
}
