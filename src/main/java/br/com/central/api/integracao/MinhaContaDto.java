package br.com.central.api.integracao;

import br.com.central.api.comercial.Periodicidade;
import br.com.central.api.comercial.SituacaoComercial;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MinhaContaDto(
        ClienteDto cliente,
        ContratacaoDto contratacao,
        List<CobrancaDto> cobrancas
) {
    public record ClienteDto(
            String nome,
            String documento,
            EnderecoDto endereco,
            List<ContatoDto> contatos
    ) {}

    public record EnderecoDto(
            String logradouro,
            String numero,
            String complemento,
            String bairro,
            String cidade,
            String uf,
            String cep
    ) {}

    public record ContatoDto(
            String nome,
            String email,
            String telefone,
            boolean principal
    ) {}

    public record ContratacaoDto(
            String planoNome,
            Periodicidade periodicidade,
            BigDecimal valor,
            int diaVencimento,
            LocalDate inicio,
            LocalDate vigenteAte,
            SituacaoComercial situacaoComercial,
            String nomeInstancia,
            List<AdicionalDto> adicionais
    ) {}

    public record AdicionalDto(
            String nome,
            BigDecimal quantidade
    ) {}

    public record CobrancaDto(
            UUID id,
            LocalDate competenciaInicio,
            LocalDate competenciaFim,
            LocalDate vencimento,
            BigDecimal valor,
            String situacao,
            boolean vencida,
            LocalDate pagoEm
    ) {}
}
