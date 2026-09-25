package br.com.central.api.comercial;

public enum Periodicidade {
    MENSAL(1),
    ANUAL(12);

    private final int meses;

    Periodicidade(int meses) {
        this.meses = meses;
    }

    public int meses() {
        return meses;
    }
}
