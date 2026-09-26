package br.com.central.api.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormatosTest {

    @Test
    void cpfValidoSaiFormatadoComOuSemPontuacao() {
        assertThat(Formatos.cpf("52998224725")).isEqualTo("529.982.247-25");
        assertThat(Formatos.cpf(" 529.982.247-25 ")).isEqualTo("529.982.247-25");
        assertThat(Formatos.cpf("  ")).isNull();
        assertThat(Formatos.cpf(null)).isNull();
    }

    @Test
    void cpfComDigitoErradoCurtoOuRepetidoEhRecusado() {
        for (String invalido : new String[] {"529.982.247-24", "101175", "111.111.111-11"}) {
            assertThatThrownBy(() -> Formatos.cpf(invalido))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("CPF inválido.");
        }
    }

    @Test
    void cnpjNumericoEAlfanumerico() {
        assertThat(Formatos.cnpj("11222333000181")).isEqualTo("11.222.333/0001-81");
        // Exemplo oficial da Receita Federal para o CNPJ alfanumérico (julho de 2026).
        assertThat(Formatos.cnpj("12.abc.345/01de-35")).isEqualTo("12.ABC.345/01DE-35");
        assertThatThrownBy(() -> Formatos.cnpj("12.ABC.345/01DE-36")).hasMessage("CNPJ inválido.");
        assertThatThrownBy(() -> Formatos.cnpj("00.000.000/0000-00")).hasMessage("CNPJ inválido.");
        assertThatThrownBy(() -> Formatos.cnpj("12.ABC.345/01DE-3A")).hasMessage("CNPJ inválido.");
    }

    @Test
    void documentoDoClienteSegueOTipo() {
        assertThat(Formatos.documento(false, "52998224725")).isEqualTo("529.982.247-25");
        assertThat(Formatos.documento(true, "11222333000181")).isEqualTo("11.222.333/0001-81");
        assertThatThrownBy(() -> Formatos.documento(true, "52998224725")).hasMessage("CNPJ inválido.");
    }

    @Test
    void cepComOitoDigitos() {
        assertThat(Formatos.cep("85800000")).isEqualTo("85800-000");
        assertThat(Formatos.cep("85.800-000")).isEqualTo("85800-000");
        assertThatThrownBy(() -> Formatos.cep("8580000")).hasMessage("CEP inválido.");
    }

    @Test
    void ufEntreOsVinteESeteEstados() {
        assertThat(Formatos.uf(" pr ")).isEqualTo("PR");
        assertThatThrownBy(() -> Formatos.uf("XX")).hasMessage("UF inválida.");
    }

    @Test
    void telefoneFixoCelularEComMaisCinquentaECinco() {
        assertThat(Formatos.telefone("45999998888")).isEqualTo("(45) 99999-8888");
        assertThat(Formatos.telefone("(45) 3222-1111")).isEqualTo("(45) 3222-1111");
        assertThat(Formatos.telefone("+55 45 99999-8888")).isEqualTo("(45) 99999-8888");
        for (String invalido : new String[] {"99999-8888", "(45) 89999-8888", "(05) 99999-8888", "(45) 1222-1111"}) {
            assertThatThrownBy(() -> Formatos.telefone(invalido))
                    .hasMessage("Telefone inválido. Informe o DDD e o número.");
        }
    }

    @Test
    void emailExigeDominioComPonto() {
        assertThat("ana@paroquia.org.br").matches(Formatos.EMAIL);
        assertThat("gustavo@teste.local").matches(Formatos.EMAIL);
        assertThat("ana@paroquia").doesNotMatch(Formatos.EMAIL);
        assertThat("ana paroquia@x.com").doesNotMatch(Formatos.EMAIL);
    }
}
