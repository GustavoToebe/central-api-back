package br.com.central.api.integracao;

import br.com.central.api.AbstractIntegrationTest;
import br.com.central.api.comercial.*;
import br.com.central.api.integracao.InstanciasVisaoService.Nivel;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InstanciasVisaoIntegrationTest extends AbstractIntegrationTest {
    @Autowired InstanciasVisaoService service;
    @Autowired ConsumoHistoricoService historico;
    @Autowired TransactionTemplate tx;
    @PersistenceContext EntityManager em;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean ConsumoCentralService consumo;

    UUID critica, atencao, boa, semDados, naoProvisionada;
    String prefixo;

    @BeforeEach
    void preparar() {
        prefixo = UUID.randomUUID().toString().substring(0, 8);
        tx.executeWithoutResult(t -> {
            var c = new Cliente(TipoCliente.PJ, br.com.central.api.Documentos.cnpj(), "Cliente " + prefixo);
            em.persist(c);
            var p = new Produto("P" + prefixo, "Produto");
            em.persist(p);
            var plano = new Plano(p, "B" + prefixo, "Plano");
            em.persist(plano);
            critica = contrato(c, p, plano, "a", SituacaoProvisionamento.ATIVA, SituacaoComercial.ATIVA);
            atencao = contrato(c, p, plano, "b", SituacaoProvisionamento.ATIVA, SituacaoComercial.TRIAL);
            boa = contrato(c, p, plano, "c", SituacaoProvisionamento.ATIVA, SituacaoComercial.ATIVA);
            semDados = contrato(c, p, plano, "d", SituacaoProvisionamento.ATIVA, SituacaoComercial.ATIVA);
            naoProvisionada = contrato(c, p, plano, "e", SituacaoProvisionamento.PENDENTE, SituacaoComercial.TRIAL);
        });
        Instant agora = Instant.now();
        historico.registrar(critica, resumo(agora, "EXCEDIDO"));
        historico.registrar(atencao, resumo(agora, "ATENCAO"));
        historico.registrar(boa, resumo(agora, "DISPONIVEL"));
    }

    UUID contrato(Cliente c, Produto p, Plano plano, String slug, SituacaoProvisionamento prov, SituacaoComercial com) {
        var x = new Contratacao(c, p, plano, Periodicidade.MENSAL, BigDecimal.TEN, 10, LocalDate.now(), com, "Paróquia " + slug,
                prefixo + slug, "Admin", "admin@example.test");
        x.setDireitosAtuais("{}");
        x.setVersaoDireitos(1);
        x.setSituacaoProvisionamento(prov);
        em.persist(x);
        em.flush();
        return x.getId();
    }

    ConsumoCentralService.Consumo resumo(Instant quando, String estado) {
        return new ConsumoCentralService.Consumo("Plano", 1, quando, quando,
                List.of(new ConsumoCentralService.Item("pessoas", "Pessoas", 5, 10L, 5L, estado, "unidade", 0, null)));
    }

    List<InstanciasVisaoService.Instancia> minhas(InstanciasVisaoService.Pagina p) {
        Set<UUID> meus = Set.of(critica, atencao, boa, semDados, naoProvisionada);
        return p.itens().stream().filter(i -> meus.contains(i.contratacaoId())).toList();
    }

    @Test
    void classificaPorGravidadeEIgnoraNaoProvisionadas() {
        var visao = service.visao(null, 0);
        var itens = minhas(visao);
        assertThat(itens).extracting(InstanciasVisaoService.Instancia::contratacaoId).doesNotContain(naoProvisionada);
        assertThat(itens).extracting(InstanciasVisaoService.Instancia::nivel)
                .containsExactly(Nivel.CRITICO, Nivel.ATENCAO, Nivel.OK, Nivel.SEM_DADOS);
        var sem = itens.stream().filter(i -> i.contratacaoId().equals(semDados)).findFirst().orElseThrow();
        assertThat(sem.consultadoEm()).isNull();
        assertThat(sem.defasado()).isTrue();
        assertThat(sem.alertas()).isEmpty();
        var critico = itens.getFirst();
        assertThat(critico.alertas()).singleElement().satisfies(a -> assertThat(a.estado()).isEqualTo("EXCEDIDO"));
        assertThat(visao.porNivel().get(Nivel.CRITICO)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void filtroPorNivelNaoAlteraOsContadoresGerais() {
        var visao = service.visao(Nivel.CRITICO, 0);
        assertThat(minhas(visao)).extracting(InstanciasVisaoService.Instancia::contratacaoId).containsExactly(critica);
        assertThat(visao.porNivel().get(Nivel.SEM_DADOS)).isGreaterThanOrEqualTo(1);
        assertThatThrownBy(() -> service.visao(null, -1)).isInstanceOf(br.com.central.api.web.BadRequestException.class);
    }

    @Test
    void varreduraConsultaPrimeiroQuemNaoTemDadosEContaFalhasSemVirarZero() {
        doThrow(new ConsumoCentralService.ConsumoIndisponivel()).when(consumo).consultar(any());
        var r = service.varrer();
        assertThat(r.consultadas()).isZero();
        assertThat(r.falhas()).isBetween(1, InstanciasVisaoService.MAXIMO_VARREDURA);
        // Falha não grava resumo novo: a instância continua sem dados.
        var sem = minhas(service.visao(null, 0)).stream().filter(i -> i.contratacaoId().equals(semDados)).findFirst().orElseThrow();
        assertThat(sem.nivel()).isEqualTo(Nivel.SEM_DADOS);
    }

    @Test
    void classificacaoPelasRegrasDeEstado() {
        var excedido = new InstanciasVisaoService.Alerta("a", "A", "EXCEDIDO", 1L, 1L);
        var inventario = new InstanciasVisaoService.Alerta("b", "B", "INVENTARIO_PENDENTE", 1L, null);
        var semLimite = new InstanciasVisaoService.Alerta("c", "C", "SEM_LIMITE_CONFIGURADO", 1L, null);
        assertThat(InstanciasVisaoService.nivel(List.of(semLimite, excedido))).isEqualTo(Nivel.CRITICO);
        assertThat(InstanciasVisaoService.nivel(List.of(inventario))).isEqualTo(Nivel.ATENCAO);
        assertThat(InstanciasVisaoService.nivel(List.of(semLimite))).isEqualTo(Nivel.OK);
        assertThat(InstanciasVisaoService.nivel(List.of())).isEqualTo(Nivel.OK);
    }
}
