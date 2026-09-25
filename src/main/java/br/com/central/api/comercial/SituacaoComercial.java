package br.com.central.api.comercial;

public enum SituacaoComercial {
    TRIAL(true),
    ATIVA(true),
    INADIMPLENTE(true),
    BLOQUEADA(false),
    CANCELADA(false);

    private final boolean acessoLiberado;

    SituacaoComercial(boolean acessoLiberado) {
        this.acessoLiberado = acessoLiberado;
    }

    public boolean isAcessoLiberado() {
        return acessoLiberado;
    }
}
