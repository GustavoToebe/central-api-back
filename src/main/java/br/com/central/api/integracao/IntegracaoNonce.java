package br.com.central.api.integracao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "integracao_nonce")
@IdClass(IntegracaoNonce.Chave.class)
public class IntegracaoNonce {

    @Id
    @Column(name = "chave_id")
    private String chaveId;

    @Id
    private String nonce;

    @Column(name = "recebido_em", nullable = false, insertable = false, updatable = false)
    private Instant recebidoEm;

    protected IntegracaoNonce() {
    }

    public static class Chave implements Serializable {
        private String chaveId;
        private String nonce;

        public Chave() {
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Chave chave)) return false;
            return Objects.equals(chaveId, chave.chaveId) && Objects.equals(nonce, chave.nonce);
        }

        @Override
        public int hashCode() {
            return Objects.hash(chaveId, nonce);
        }
    }
}
