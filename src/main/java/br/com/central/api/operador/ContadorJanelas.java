package br.com.central.api.operador;

/**
 * Contador de tentativas por chave dentro de uma janela fixa. {@link #tentar} conta a tentativa de forma atômica e devolve o
 * estado já com ela somada; passada a janela, a contagem recomeça em 1.
 */
interface ContadorJanelas {
    record Estado(int tentativas, long ateMs) {}

    Estado tentar(String chave, long agoraMs, long janelaMs);
}
