package br.com.central.api.integracao;

import static org.assertj.core.api.Assertions.*;

import br.com.central.api.AbstractIntegrationTest;
import br.com.central.api.comercial.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class ConsumoHistoricoIntegrationTest extends AbstractIntegrationTest {
  @Autowired ConsumoHistoricoService service;
  @Autowired TransactionTemplate tx;
  @PersistenceContext EntityManager em;
  UUID id, outra;

  @BeforeEach
  void preparar() {
    tx.executeWithoutResult(
        t -> {
          String s = UUID.randomUUID().toString().substring(0, 8);
          var c = new Cliente(TipoCliente.PJ, br.com.central.api.Documentos.cnpj(), "Cliente " + s);
          em.persist(c);
          var p = new Produto("P" + s, "Produto");
          em.persist(p);
          var plano = new Plano(p, "B" + s, "Plano");
          em.persist(plano);
          id = contrato(c, p, plano, s);
          outra = contrato(c, p, plano, s + "b");
        });
  }

  UUID contrato(Cliente c, Produto p, Plano plano, String slug) {
    var x =
        new Contratacao(
            c,
            p,
            plano,
            Periodicidade.MENSAL,
            BigDecimal.TEN,
            10,
            LocalDate.now(),
            SituacaoComercial.TRIAL,
            "Paróquia",
            slug,
            "Admin",
            "admin@example.test");
    x.setDireitosAtuais("{}");
    x.setVersaoDireitos(1);
    em.persist(x);
    em.flush();
    return x.getId();
  }

  ConsumoCentralService.Consumo consumo(Instant momento, long usado) {
    return new ConsumoCentralService.Consumo(
        "Plano",
        1,
        momento,
        momento,
        List.of(
            new ConsumoCentralService.Item(
                "pessoas", "Pessoas", usado, 10L, 10 - usado, "DISPONIVEL", "unidade", 0, null)));
  }

  @Test
  void historiaVaziaNaoSignificaConsumoZero() {
    assertThat(service.listar(id)).isEmpty();
  }

  @Test
  void substituiSomentePorConsultaMaisRecenteDoMesmoDia() {
    Instant a =
        LocalDate.now(ZoneId.of("America/Sao_Paulo"))
            .atTime(12, 0)
            .atZone(ZoneId.of("America/Sao_Paulo"))
            .toInstant();
    service.registrar(id, consumo(a, 2));
    service.registrar(id, consumo(a.plusSeconds(60), 4));
    service.registrar(id, consumo(a, 1));
    assertThat(service.listar(id)).hasSize(1);
    assertThat(service.listar(id).getFirst().consumo().itens().getFirst().usado()).isEqualTo(4);
  }

  @Test
  void contratosNaoCompartilhamHistorico() {
    service.registrar(id, consumo(Instant.now(), 3));
    assertThat(service.listar(outra)).isEmpty();
  }

  @Test
  void retencaoRemoveDiasForaDaJanela() {
    service.registrar(id, consumo(Instant.now().minus(Duration.ofDays(100)), 2));
    service.registrar(id, consumo(Instant.now(), 4));
    assertThat(service.listar(id)).hasSize(1);
    Long total =
        tx.execute(
            t ->
                em.createQuery(
                        "select count(h) from ConsumoHistorico h where h.escopo=:id", Long.class)
                    .setParameter("id", id)
                    .getSingleResult());
    assertThat(total).isEqualTo(1);
  }

  @Test
  void diaDaConsultaSegueBrasilia() {
    var dia = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    var madrugada = dia.atTime(1, 0).toInstant(ZoneOffset.UTC);
    service.registrar(id, consumo(madrugada, 3));
    assertThat(service.listar(id).getFirst().dia()).isEqualTo(dia.minusDays(1));
  }
}
