package br.com.central.api;

import java.util.concurrent.ThreadLocalRandom;

/** CPF e CNPJ válidos e aleatórios para teste (o cliente exige documento válido e único). */
public final class Documentos {

    private Documentos() {
    }

    public static String cpf() {
        int[] d = new int[11];
        for (int i = 0; i < 9; i++) {
            d[i] = ThreadLocalRandom.current().nextInt(10);
        }
        d[9] = dv(d, 9, 10);
        d[10] = dv(d, 10, 11);
        StringBuilder sb = new StringBuilder();
        for (int n : d) {
            sb.append(n);
        }
        return sb.toString();
    }

    public static String cnpj() {
        int[] d = new int[14];
        for (int i = 0; i < 12; i++) {
            d[i] = ThreadLocalRandom.current().nextInt(10);
        }
        d[12] = dvCnpj(d, 12);
        d[13] = dvCnpj(d, 13);
        StringBuilder sb = new StringBuilder();
        for (int n : d) {
            sb.append(n);
        }
        return sb.toString();
    }

    private static int dv(int[] d, int tamanho, int pesoInicial) {
        int soma = 0;
        for (int i = 0; i < tamanho; i++) {
            soma += d[i] * (pesoInicial - i);
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private static int dvCnpj(int[] d, int tamanho) {
        int soma = 0;
        int peso = tamanho - 7;
        for (int i = 0; i < tamanho; i++) {
            soma += d[i] * peso;
            peso = peso == 2 ? 9 : peso - 1;
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
