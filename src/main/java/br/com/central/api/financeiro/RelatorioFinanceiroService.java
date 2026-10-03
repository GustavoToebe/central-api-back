package br.com.central.api.financeiro;

import br.com.central.api.comercial.Cobranca;
import br.com.central.api.comercial.CobrancaRepository;
import br.com.central.api.web.BadRequestException;
import br.com.central.api.web.ResourceNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static br.com.central.api.financeiro.MovimentoFinanceiro.Situacao;
import static br.com.central.api.financeiro.MovimentoFinanceiro.Tipo;
import static br.com.central.api.financeiro.RelatorioFinanceiroDtos.*;

/**
 * Relatórios do financeiro do operador. Todos leem e nunca gravam, num único retrato do banco (REPEATABLE_READ), e limitam
 * o tamanho: passou de 5.000 lançamentos no período, o operador reduz o período em vez de receber um relatório cortado.
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class RelatorioFinanceiroService {
    static final int LIMITE = 5000;
    private static final LocalDate INICIO = LocalDate.of(1900, 1, 1);
    private static final String OBSERVACAO_BANCO = "Cobranças de assinaturas não têm vínculo bancário e não entram aqui; ver o demonstrativo do resultado.";

    private final MovimentoFinanceiroRepository movimentos;
    private final CategoriaFinanceiraRepository categorias;
    private final ContaFinanceiraRepository contas;
    private final CobrancaRepository cobrancas;
    private final FinanceiroService financeiro;

    public RelatorioFinanceiroService(MovimentoFinanceiroRepository movimentos, CategoriaFinanceiraRepository categorias,
                                      ContaFinanceiraRepository contas, CobrancaRepository cobrancas, FinanceiroService financeiro) {
        this.movimentos = movimentos; this.categorias = categorias; this.contas = contas; this.cobrancas = cobrancas; this.financeiro = financeiro;
    }

    /** Despesas ou receitas agrupadas por grupo e conta contábil, com os lançamentos de cada conta. */
    public PorTipo porTipo(Tipo tipo, Visao visao, LocalDate de, LocalDate ate) {
        intervalo(de, ate);
        var todas = categorias.findAll().stream().collect(Collectors.toMap(CategoriaFinanceira::getId, c -> c));
        List<MovimentoFinanceiro> lista = buscar(tipo, visao, de, ate, null);
        Map<UUID, Map<UUID, List<Lancamento>>> porGrupo = new LinkedHashMap<>();
        for (var m : lista) {
            var conta = todas.get(m.getCategoria().getId());
            UUID grupoId = conta.getGrupoId() == null ? conta.getId() : conta.getGrupoId();
            porGrupo.computeIfAbsent(grupoId, k -> new LinkedHashMap<>()).computeIfAbsent(conta.getId(), k -> new ArrayList<>())
                    .add(new Lancamento(m.getId(), data(m, visao), m.getDescricao(), m.getConta().getNome(), m.getValor()));
        }
        List<GrupoLinha> grupos = new ArrayList<>();
        for (var e : porGrupo.entrySet()) {
            var grupo = todas.get(e.getKey());
            List<ContaContabilLinha> linhas = new ArrayList<>();
            for (var c : e.getValue().entrySet()) {
                var lanc = c.getValue();
                linhas.add(new ContaContabilLinha(c.getKey(), todas.get(c.getKey()).getNome(), soma(lanc), lanc));
            }
            linhas.sort(Comparator.comparing(ContaContabilLinha::nome, String.CASE_INSENSITIVE_ORDER));
            grupos.add(new GrupoLinha(grupo.getId(), grupo.getNome(), false, linhas.stream().map(ContaContabilLinha::total).reduce(BigDecimal.ZERO, BigDecimal::add), linhas));
        }
        if (tipo == Tipo.RECEITA) comercial(visao, de, ate).ifPresent(grupos::add);
        grupos.sort(Comparator.comparing(GrupoLinha::nome, String.CASE_INSENSITIVE_ORDER));
        return new PorTipo(de, ate, visao, tipo, grupos.stream().map(GrupoLinha::total).reduce(BigDecimal.ZERO, BigDecimal::add), grupos);
    }

    /** Movimentação de cada conta/banco no período: saldo anterior, entradas, saídas e saldo corrido (só baixas). */
    public BancoCaixa bancoCaixa(LocalDate de, LocalDate ate, UUID contaId) {
        intervalo(de, ate);
        List<ContaFinanceira> alvo = contaId == null ? contas.findAllByOrderByNomeAsc()
                : List.of(contas.findById(contaId).orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada.")));
        Map<UUID, BigDecimal> acumuladoAnterior = new HashMap<>();
        for (var t : movimentos.totais(INICIO, de.minusDays(1))) acumuladoAnterior.put(t.getContaId(), t.getReceitas().subtract(t.getDespesas()));
        Map<UUID, List<MovimentoFinanceiro>> doPeriodo = buscar(null, Visao.REALIZADO, de, ate, contaId).stream()
                .collect(Collectors.groupingBy(m -> m.getConta().getId(), LinkedHashMap::new, Collectors.toList()));
        List<ContaBanco> linhas = new ArrayList<>();
        BigDecimal anteriorTotal = BigDecimal.ZERO, entradasTotal = BigDecimal.ZERO, saidasTotal = BigDecimal.ZERO;
        for (var c : alvo) {
            BigDecimal anterior = c.getDataSaldoInicial().isAfter(de.minusDays(1)) ? BigDecimal.ZERO
                    : c.getSaldoInicial().add(acumuladoAnterior.getOrDefault(c.getId(), BigDecimal.ZERO));
            BigDecimal saldo = anterior, entradas = BigDecimal.ZERO, saidas = BigDecimal.ZERO;
            List<MovimentoBanco> mov = new ArrayList<>();
            boolean abre = !c.getDataSaldoInicial().isBefore(de) && !c.getDataSaldoInicial().isAfter(ate) && c.getSaldoInicial().signum() != 0;
            if (abre) {
                BigDecimal v = c.getSaldoInicial();
                saldo = saldo.add(v);
                BigDecimal ent = v.signum() > 0 ? v : BigDecimal.ZERO, sai = v.signum() < 0 ? v.negate() : BigDecimal.ZERO;
                entradas = entradas.add(ent); saidas = saidas.add(sai);
                mov.add(new MovimentoBanco(null, c.getDataSaldoInicial(), "Saldo inicial da conta", "—", ent, sai, saldo));
            }
            for (var m : doPeriodo.getOrDefault(c.getId(), List.of())) {
                boolean entrada = m.getTipo() == Tipo.RECEITA;
                saldo = entrada ? saldo.add(m.getValor()) : saldo.subtract(m.getValor());
                if (entrada) entradas = entradas.add(m.getValor()); else saidas = saidas.add(m.getValor());
                mov.add(new MovimentoBanco(m.getId(), m.getDataPagamento(), m.getDescricao(), m.getCategoria().getNome(),
                        entrada ? m.getValor() : BigDecimal.ZERO, entrada ? BigDecimal.ZERO : m.getValor(), saldo));
            }
            if (mov.isEmpty() && anterior.signum() == 0 && !c.isAtivo()) continue;
            linhas.add(new ContaBanco(c.getId(), c.getNome(), c.isAtivo(), anterior, entradas, saidas, saldo, mov));
            anteriorTotal = anteriorTotal.add(anterior); entradasTotal = entradasTotal.add(entradas); saidasTotal = saidasTotal.add(saidas);
        }
        return new BancoCaixa(de, ate, anteriorTotal, entradasTotal, saidasTotal, anteriorTotal.add(entradasTotal).subtract(saidasTotal), OBSERVACAO_BANCO, linhas);
    }

    /** Resultado do período: receitas e despesas realizadas por grupo e conta, resultado, previsto e saldos das contas ao fim do período. */
    public Demonstrativo demonstrativo(LocalDate de, LocalDate ate) {
        var receitas = semDetalhe(porTipo(Tipo.RECEITA, Visao.REALIZADO, de, ate));
        var despesas = semDetalhe(porTipo(Tipo.DESPESA, Visao.REALIZADO, de, ate));
        var resumo = financeiro.resumo(de, ate);
        return new Demonstrativo(de, ate, receitas, despesas, receitas.total(), despesas.total(), receitas.total().subtract(despesas.total()),
                resumo.receberPrevisto(), resumo.pagarPrevisto(), resumo.resultadoPrevisto(), resumo.contas());
    }

    private static PorTipo semDetalhe(PorTipo p) {
        List<GrupoLinha> grupos = p.grupos().stream().map(g -> new GrupoLinha(g.id(), g.nome(), g.comercial(), g.total(),
                g.contas().stream().map(c -> new ContaContabilLinha(c.id(), c.nome(), c.total(), List.of())).toList())).toList();
        return new PorTipo(p.de(), p.ate(), p.visao(), p.tipo(), p.total(), grupos);
    }

    private Optional<GrupoLinha> comercial(Visao visao, LocalDate de, LocalDate ate) {
        List<Cobranca> lista = visao == Visao.REALIZADO ? cobrancas.pagasNoPeriodo(Cobranca.Status.PAGA, de, ate) : cobrancas.abertasNoPeriodo(de, ate);
        if (lista.isEmpty()) return Optional.empty();
        List<Lancamento> lanc = lista.stream().map(c -> new Lancamento(c.getId(), visao == Visao.REALIZADO ? c.getPagoEm() : c.getVencimento(),
                "Cobrança nº " + c.getSequencial(), "Sem vínculo bancário", visao == Visao.REALIZADO ? c.getValorPago() : c.getValor())).toList();
        BigDecimal total = soma(lanc);
        return Optional.of(new GrupoLinha(null, "Assinaturas dos aplicativos", true, total,
                List.of(new ContaContabilLinha(null, visao == Visao.REALIZADO ? "Cobranças pagas" : "Cobranças em aberto", total, lanc))));
    }

    private List<MovimentoFinanceiro> buscar(Tipo tipo, Visao visao, LocalDate de, LocalDate ate, UUID contaId) {
        String campoData = visao == Visao.REALIZADO ? "dataPagamento" : "vencimento";
        var situacao = visao == Visao.REALIZADO ? Situacao.PAGO : Situacao.PENDENTE;
        var pagina = movimentos.findAll((root, q, cb) -> {
            var p = new ArrayList<jakarta.persistence.criteria.Predicate>();
            p.add(cb.equal(root.get("situacao"), situacao));
            p.add(cb.between(root.get(campoData), de, ate));
            if (tipo != null) p.add(cb.equal(root.get("tipo"), tipo));
            if (contaId != null) p.add(cb.equal(root.get("conta").get("id"), contaId));
            return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, PageRequest.of(0, LIMITE + 1, Sort.by(campoData).ascending().and(Sort.by("descricao")).and(Sort.by("id"))));
        if (pagina.getContent().size() > LIMITE) throw new BadRequestException("O período tem mais de " + LIMITE + " lançamentos; reduza o período do relatório.");
        return pagina.getContent();
    }

    private static LocalDate data(MovimentoFinanceiro m, Visao visao) { return visao == Visao.REALIZADO ? m.getDataPagamento() : m.getVencimento(); }
    private static BigDecimal soma(List<Lancamento> l) { return l.stream().map(Lancamento::valor).reduce(BigDecimal.ZERO, BigDecimal::add); }
    private static void intervalo(LocalDate de, LocalDate ate) {
        if (de == null || ate == null || de.isAfter(ate) || de.isBefore(INICIO)) throw new BadRequestException("Informe um período válido, a partir de 1900.");
    }
}
