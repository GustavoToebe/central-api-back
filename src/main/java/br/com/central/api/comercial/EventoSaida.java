package br.com.central.api.comercial;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evento_saida")
public class EventoSaida {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "contratacao_id", nullable = false)
    private UUID contratacaoId;

    @Column(name = "produto_codigo", nullable = false, length = 40)
    private String produtoCodigo;

    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(nullable = false)
    private int versao;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SituacaoEvento situacao = SituacaoEvento.PENDENTE;

    @Column(nullable = false)
    private int tentativas;

    @Column(name = "proxima_tentativa", nullable = false)
    private Instant proximaTentativa;

    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "reservado_por")
    private UUID reservadoPor;

    @Column(name = "reserva_ate")
    private Instant reservaAte;

    public boolean reservaAtiva(Instant agora) {
        return reservadoPor != null && reservaAte != null && reservaAte.isAfter(agora);
    }

    public void reservar(UUID dono, Instant ate) {
        reservadoPor = dono;
        reservaAte = ate;
    }

    public boolean pertenceA(UUID dono) {
        return dono != null && dono.equals(reservadoPor);
    }

    public void liberarReserva() {
        reservadoPor = null;
        reservaAte = null;
    }

    public UUID getReservadoPor() { return reservadoPor; }
    public Instant getReservaAte() { return reservaAte; }

    protected EventoSaida() {
    }

    public EventoSaida(UUID contratacaoId, String produtoCodigo, String tipo, int versao, String payload,
                       Instant proximaTentativa) {
        this.contratacaoId = contratacaoId;
        this.produtoCodigo = produtoCodigo;
        this.tipo = tipo;
        this.versao = versao;
        this.payload = payload;
        this.proximaTentativa = proximaTentativa;
    }

    public void descartar() {
        this.situacao = SituacaoEvento.DESCARTADO;
    }

    public void marcarEnviado() {
        this.situacao = SituacaoEvento.ENVIADO;
    }

    public void marcarFalhou() {
        this.situacao = SituacaoEvento.FALHOU;
    }

    /** Soma uma tentativa e empurra a próxima para o instante calculado pela agenda. */
    public void reagendar(Instant quando) {
        this.tentativas++;
        this.proximaTentativa = quando;
    }

    /** O operador pediu para tentar agora; a contagem de tentativas fica como está. */
    public void adiantar(Instant quando) {
        this.proximaTentativa = quando;
    }

    public UUID getId() {
        return id;
    }

    public UUID getContratacaoId() {
        return contratacaoId;
    }

    public int getVersao() {
        return versao;
    }

    public String getPayload() {
        return payload;
    }

    public SituacaoEvento getSituacao() {
        return situacao;
    }

    public String getTipo() {
        return tipo;
    }

    public int getTentativas() {
        return tentativas;
    }

    public Instant getProximaTentativa() {
        return proximaTentativa;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
