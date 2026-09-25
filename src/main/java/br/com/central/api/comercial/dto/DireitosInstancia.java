package br.com.central.api.comercial.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DireitosInstancia(
        UUID contratacaoId,
        UUID clienteId,
        String produto,
        UUID tenantId,
        int versao,
        String situacao,
        boolean acessoLiberado,
        String motivoBloqueio,
        LocalDate vigenteAte,
        PlanoResumo plano,
        Map<String, BigDecimal> limites,
        List<String> funcionalidades,
        Instant geradoEm
) {
    public record PlanoResumo(String codigo, String nome) {
    }
}
