package br.com.central.api.comercial;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class PlanoRecursoId implements Serializable {

    private UUID planoId;
    private UUID recursoId;

    public PlanoRecursoId() {
    }

    public PlanoRecursoId(UUID planoId, UUID recursoId) {
        this.planoId = planoId;
        this.recursoId = recursoId;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof PlanoRecursoId id)) {
            return false;
        }
        return Objects.equals(planoId, id.planoId) && Objects.equals(recursoId, id.recursoId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(planoId, recursoId);
    }
}
