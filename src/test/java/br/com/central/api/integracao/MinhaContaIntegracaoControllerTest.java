package br.com.central.api.integracao;

import br.com.central.api.AbstractIntegrationTest;
import br.com.central.api.Documentos;
import br.com.central.api.comercial.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class MinhaContaIntegracaoControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private ContratacaoRepository contratacaoRepository;

    @Autowired
    private PlanoRepository planoRepository;

    @Autowired
    private CobrancaRepository cobrancaRepository;

    private String assinar(String caminhoCru, String timestamp, String nonce) throws Exception {
        return HmacAssinatura.assinar(
                "segredo-de-teste-nao-usar-em-producao-0123456789".getBytes(StandardCharsets.UTF_8),
                "GET",
                caminhoCru,
                timestamp,
                nonce,
                new byte[0]
        );
    }

    @Test
    void buscaDadosDaMinhaConta() throws Exception {
        Produto produto = produtoRepository.findByCodigoIgnoreCase("SERVIREA").orElseGet(() -> produtoRepository.saveAndFlush(new Produto("SERVIREA", "Servirea")));

        Plano plano = new Plano(produto, "BASE", "Plano Base");
        plano.setAtivo(true);
        plano = planoRepository.saveAndFlush(plano);

        String documento = Documentos.cnpj();
        Cliente cliente = new Cliente(TipoCliente.PJ, documento, "Paroquia Teste");
        cliente = clienteRepository.saveAndFlush(cliente);

        Contratacao contratacao = new Contratacao(cliente, produto, plano, Periodicidade.MENSAL,
                BigDecimal.valueOf(100), 10, LocalDate.now(ComercialConfiguration.FUSO), SituacaoComercial.ATIVA,
                "Paroquia Teste", "paroquia-teste", "Admin", "admin@teste.com");
        UUID idExterno = UUID.randomUUID();
        contratacao.setIdExterno(idExterno);
        contratacao.setDireitosAtuais("{\"versao\": 1, \"acessoLiberado\": true}");
        contratacao.setVersaoDireitos(1);
        contratacao = contratacaoRepository.saveAndFlush(contratacao);

        // Cobrancas da contratacao correta
        LocalDate hoje = LocalDate.now(ComercialConfiguration.FUSO);
        Cobranca cobranca1 = new Cobranca(contratacao, hoje.minusMonths(1), hoje.minusMonths(1).plusDays(30),
                hoje.minusDays(5), List.of(CobrancaItem.plano("Plano", BigDecimal.valueOf(100))));
        cobranca1 = cobrancaRepository.saveAndFlush(cobranca1);

        Cobranca cobranca2 = new Cobranca(contratacao, hoje, hoje.plusDays(30),
                hoje.plusDays(25), List.of(CobrancaItem.plano("Plano", BigDecimal.valueOf(100))));
        cobranca2 = cobrancaRepository.saveAndFlush(cobranca2);

        // Contratacao concorrente
        Contratacao contratacao2 = new Contratacao(cliente, produto, plano, Periodicidade.MENSAL,
                BigDecimal.valueOf(100), 10, LocalDate.now(ComercialConfiguration.FUSO), SituacaoComercial.ATIVA,
                "Paroquia Outra", "paroquia-outra", "Admin", "admin@teste.com");
        contratacao2.setIdExterno(UUID.randomUUID());
        contratacao2.setDireitosAtuais("{\"versao\": 1, \"acessoLiberado\": true}");
        contratacao2.setVersaoDireitos(1);
        contratacao2 = contratacaoRepository.saveAndFlush(contratacao2);
        
        Cobranca cobranca3 = new Cobranca(contratacao2, hoje, hoje.plusDays(30),
                hoje.plusDays(25), List.of(CobrancaItem.plano("Plano", BigDecimal.valueOf(100))));
        cobrancaRepository.saveAndFlush(cobranca3);

        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        String nonce = UUID.randomUUID().toString();
        String caminhoCru = "/integracao/v1/produtos/SERVIREA/instancias/" + idExterno + "/minha-conta";
        String assinatura = assinar(caminhoCru, timestamp, nonce);

        mvc.perform(get(caminhoCru)
                .header("X-Integracao-Chave", "teste-servire")
                .header("X-Integracao-Timestamp", timestamp)
                .header("X-Integracao-Nonce", nonce)
                .header("X-Integracao-Assinatura", assinatura))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cliente.nome").value("Paroquia Teste"))
                .andExpect(jsonPath("$.cliente.documento").value(documento))
                .andExpect(jsonPath("$.cliente.endereco").isMap())
                .andExpect(jsonPath("$.cliente.contatos").isArray())
                .andExpect(jsonPath("$.contratacao.planoNome").value("Plano Base"))
                .andExpect(jsonPath("$.contratacao.periodicidade").value("MENSAL"))
                .andExpect(jsonPath("$.contratacao.situacaoComercial").value("ATIVA"))
                .andExpect(jsonPath("$.contratacao.diaVencimento").value(10))
                .andExpect(jsonPath("$.contratacao.adicionais").isArray())
                .andExpect(jsonPath("$.cobrancas[0].situacao").value("ABERTA"))
                .andExpect(jsonPath("$.cobrancas[0].competenciaInicio").exists())
                .andExpect(jsonPath("$.cobrancas").isArray())
                .andExpect(jsonPath("$.cobrancas.length()").value(2))
                .andExpect(jsonPath("$.cobrancas[0].id").value(cobranca2.getId().toString()))
                .andExpect(jsonPath("$.cobrancas[1].id").value(cobranca1.getId().toString()))
                .andExpect(jsonPath("$.cobrancas[1].vencida").value(true))
                .andExpect(jsonPath("$.cobrancas[0].vencida").value(false))
                .andExpect(jsonPath("$.contratacao.isencaoMotivo").doesNotExist())
                .andExpect(jsonPath("$.contratacao.idempotencyKey").doesNotExist())
                .andExpect(jsonPath("$.contratacao.direitos").doesNotExist())
                .andExpect(jsonPath("$.contratacao.adminEmail").doesNotExist())
                .andExpect(jsonPath("$.contratacao.slugInstancia").doesNotExist())
                .andExpect(jsonPath("$.contratacao.situacaoProvisionamento").doesNotExist());
    }

    @Test
    void retorna404ParaInstanciaInexistente() throws Exception {
        Produto produto = produtoRepository.findByCodigoIgnoreCase("SERVIREA").orElseGet(() -> produtoRepository.saveAndFlush(new Produto("SERVIREA", "Servirea")));

        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        String nonce = UUID.randomUUID().toString();
        String caminhoCru = "/integracao/v1/produtos/SERVIREA/instancias/" + UUID.randomUUID() + "/minha-conta";
        String assinatura = assinar(caminhoCru, timestamp, nonce);

        mvc.perform(get(caminhoCru)
                .header("X-Integracao-Chave", "teste-servire")
                .header("X-Integracao-Timestamp", timestamp)
                .header("X-Integracao-Nonce", nonce)
                .header("X-Integracao-Assinatura", assinatura))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna404ParaProdutoInexistente() throws Exception {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        String nonce = UUID.randomUUID().toString();
        String caminhoCru = "/integracao/v1/produtos/INEXISTENTE/instancias/" + UUID.randomUUID() + "/minha-conta";
        String assinatura = assinar(caminhoCru, timestamp, nonce);

        mvc.perform(get(caminhoCru)
                .header("X-Integracao-Chave", "teste-servire")
                .header("X-Integracao-Timestamp", timestamp)
                .header("X-Integracao-Nonce", nonce)
                .header("X-Integracao-Assinatura", assinatura))
                .andExpect(status().isNotFound());
    }

    @Test
    void recusaRequisicaoSemAssinatura() throws Exception {
        mvc.perform(get("/integracao/v1/produtos/SERVIREA/instancias/" + UUID.randomUUID() + "/minha-conta"))
                .andExpect(status().isUnauthorized());
    }
}
