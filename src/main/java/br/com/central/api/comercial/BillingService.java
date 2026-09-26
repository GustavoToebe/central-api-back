package br.com.central.api.comercial;

import br.com.central.api.comercial.dto.FinanceiroDtos.CobrancaDetalhe;
import br.com.central.api.comercial.dto.FinanceiroDtos.CobrancaItemResponse;
import br.com.central.api.comercial.dto.FinanceiroDtos.CobrancaLinha;
import br.com.central.api.comercial.dto.FinanceiroDtos.CobrancaResponse;
import br.com.central.api.comercial.dto.FinanceiroDtos.FiltroCobrancas;
import br.com.central.api.comercial.dto.FinanceiroDtos.FinanceiroResponse;
import br.com.central.api.comercial.dto.FinanceiroDtos.RegistrarPagamentoRequest;
import br.com.central.api.comercial.dto.FinanceiroDtos.Resumo;
import br.com.central.api.comercial.dto.FinanceiroDtos.SituacaoFinanceira;
import br.com.central.api.security.OperadorAutenticado;
import br.com.central.api.web.BadRequestException;
import br.com.central.api.web.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cobrança manual por contratação, adaptada do billing do Servire.
 * O job diário gera cobranças e marca INADIMPLENTE. Bloqueio continua manual.
 *
 * <p>Desde 26/09/2026 (teste de telas) a competência começa sempre no dia 1 do
 * mês de início, em qualquer periodicidade, e a cobrança tem itens: o plano do
 * período e cada adicional (preço mensal × quantidade × meses do período).
 */
@Service
public class BillingService {

    static final int MESES_ANTECEDENCIA = 1;
    static final int MAX_MESES_ADIANTADOS = 36;
    static final int MAX_LINHAS_LISTA = 1000;

    private static final DateTimeFormatter MES_ANO = DateTimeFormatter.ofPattern("MM/yyyy");

    private final CobrancaRepository cobrancaRepository;
    private final ContratacaoRepository contratacaoRepository;
    private final ContratacaoAdicionalRepository contratacaoAdicionalRepository;
    private final DireitosDaContratacao direitos;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public BillingService(CobrancaRepository cobrancaRepository, ContratacaoRepository contratacaoRepository,
                          ContratacaoAdicionalRepository contratacaoAdicionalRepository,
                          DireitosDaContratacao direitos, Clock clock) {
        this.cobrancaRepository = cobrancaRepository;
        this.contratacaoRepository = contratacaoRepository;
        this.contratacaoAdicionalRepository = contratacaoAdicionalRepository;
        this.direitos = direitos;
        this.clock = clock;
    }

    public LocalDate hoje() {
        return LocalDate.now(clock);
    }

    @Transactional
    public FinanceiroResponse financeiro(UUID contratacaoId) {
        Contratacao contratacao = carregar(contratacaoId);
        if (contratacao.getSituacaoComercial() != SituacaoComercial.CANCELADA) {
            gerarCobrancas(contratacao, hoje().plusMonths(MESES_ANTECEDENCIA));
        }
        return montar(contratacaoId);
    }

    /**
     * Cria só as cobranças que faltam com competência entre {@code de} e
     * {@code ate}. As que já existem não mudam (26/09/2026: o operador achou
     * que "gerar até" mexia nas antigas; quem refaz abertas é a troca de plano).
     */
    @Transactional
    public FinanceiroResponse gerarAdiantadas(UUID contratacaoId, YearMonth de, YearMonth ate) {
        Contratacao contratacao = carregar(contratacaoId);
        exigirNaoCancelada(contratacao);
        if (de != null && de.isAfter(ate)) {
            throw new BadRequestException("A competência inicial não pode ser depois da final.");
        }
        YearMonth limite = YearMonth.from(hoje()).plusMonths(MAX_MESES_ADIANTADOS);
        if (ate.isAfter(limite)) {
            throw new BadRequestException("Gere no máximo " + MAX_MESES_ADIANTADOS + " meses à frente.");
        }
        gerarCobrancas(contratacao, de == null ? null : de.atDay(1), ate.atEndOfMonth());
        return montar(contratacaoId);
    }

    @Transactional
    public FinanceiroResponse registrarPagamento(UUID contratacaoId, RegistrarPagamentoRequest request) {
        Contratacao contratacao = carregar(contratacaoId);
        exigirNaoCancelada(contratacao);
        List<UUID> ids = validarPagamento(request);
        pagar(contratacao, buscarCobrancas(contratacaoId, ids), request);
        return montar(contratacaoId);
    }

    /**
     * Pagamento pela tela "Cobranças": as cobranças podem ser de clientes
     * diferentes. Cada contratação é tratada como no pagamento individual.
     */
    @Transactional
    public int registrarPagamentos(RegistrarPagamentoRequest request) {
        List<UUID> ids = validarPagamento(request);
        List<Cobranca> cobrancas = cobrancaRepository.findAllById(ids);
        if (cobrancas.size() != ids.size()) {
            throw new ResourceNotFoundException("Cobrança não encontrada.");
        }
        Map<UUID, List<Cobranca>> porContratacao = new LinkedHashMap<>();
        for (Cobranca cobranca : cobrancas) {
            porContratacao.computeIfAbsent(cobranca.getContratacaoId(), chave -> new ArrayList<>()).add(cobranca);
        }
        for (Map.Entry<UUID, List<Cobranca>> grupo : porContratacao.entrySet()) {
            Contratacao contratacao = carregar(grupo.getKey());
            exigirNaoCancelada(contratacao);
            pagar(contratacao, grupo.getValue(), request);
        }
        return cobrancas.size();
    }

    @Transactional
    public FinanceiroResponse estornar(UUID contratacaoId, UUID cobrancaId) {
        carregar(contratacaoId);
        Cobranca cobranca = buscarCobrancas(contratacaoId, List.of(cobrancaId)).getFirst();
        if (cobranca.getStatus() != Cobranca.Status.PAGA) {
            throw new BadRequestException("Só é possível estornar cobrança paga.");
        }
        cobranca.estornar();
        return montar(contratacaoId);
    }

    @Transactional
    public FinanceiroResponse isentar(UUID contratacaoId, UUID cobrancaId, String motivo) {
        Contratacao contratacao = carregar(contratacaoId);
        Cobranca cobranca = buscarCobrancas(contratacaoId, List.of(cobrancaId)).getFirst();
        if (cobranca.getStatus() != Cobranca.Status.ABERTA) {
            throw new BadRequestException("Só é possível isentar cobrança em aberto.");
        }
        String texto = texto(motivo);
        cobranca.cancelar(texto == null ? "Isenta" : texto);
        entityManager.flush();
        if (contratacao.getSituacaoComercial() == SituacaoComercial.INADIMPLENTE
                && !cobrancaRepository.existsByContratacaoIdAndStatusAndVencimentoBefore(
                contratacaoId, Cobranca.Status.ABERTA, hoje())) {
            contratacao.setSituacaoComercial(SituacaoComercial.ATIVA);
            direitos.publicar(contratacao, "ISENCAO", texto);
        }
        return montar(contratacaoId);
    }

    /**
     * Gera as cobranças que faltam e, se houver vencida em aberto, marca
     * INADIMPLENTE. Não bloqueia.
     */
    @Transactional
    public int gerarCobrancasDeTodas() {
        LocalDate ate = hoje().plusMonths(MESES_ANTECEDENCIA);
        int total = 0;
        List<Contratacao> ativas = contratacaoRepository.findBySituacaoComercialIn(List.of(
                SituacaoComercial.TRIAL, SituacaoComercial.ATIVA, SituacaoComercial.INADIMPLENTE,
                SituacaoComercial.BLOQUEADA));
        for (Contratacao contratacao : ativas) {
            total += gerarCobrancas(contratacao, ate);
        }
        entityManager.flush();
        for (Contratacao contratacao : contratacaoRepository.findBySituacaoComercialIn(List.of(
                SituacaoComercial.TRIAL, SituacaoComercial.ATIVA))) {
            if (cobrancaRepository.existsByContratacaoIdAndStatusAndVencimentoBefore(
                    contratacao.getId(), Cobranca.Status.ABERTA, hoje())) {
                contratacao.setSituacaoComercial(SituacaoComercial.INADIMPLENTE);
                direitos.publicar(contratacao, "INADIMPLENTE", "Cobrança vencida");
            }
        }
        return total;
    }

    @Transactional(readOnly = true)
    public Map<UUID, SituacaoFinanceira> situacaoPorContratacao(Collection<UUID> ids) {
        Map<UUID, SituacaoFinanceira> resultado = new HashMap<>();
        if (ids.isEmpty()) {
            return resultado;
        }
        LocalDate hoje = hoje();
        Map<UUID, Object[]> atrasos = new HashMap<>();
        for (Object[] linha : cobrancaRepository.resumoAtraso(Cobranca.Status.ABERTA, hoje)) {
            atrasos.put((UUID) linha[0], linha);
        }
        for (Contratacao contratacao : contratacaoRepository.findAllById(ids)) {
            Object[] atraso = atrasos.get(contratacao.getId());
            long vencidas = atraso == null ? 0 : ((Number) atraso[1]).longValue();
            long dias = atraso == null ? 0 : ChronoUnit.DAYS.between((LocalDate) atraso[2], hoje);
            resultado.put(contratacao.getId(), new SituacaoFinanceira(
                    contratacao.getPlano().getNome(), contratacao.getPeriodicidade(), vencidas, dias));
        }
        return resultado;
    }

    @Transactional(readOnly = true)
    public long contarContratacoesEmAtraso() {
        return cobrancaRepository.contarContratacoesEmAtraso(Cobranca.Status.ABERTA, hoje());
    }

    @Transactional(readOnly = true)
    public BigDecimal recebidoNoMes() {
        YearMonth mes = YearMonth.from(hoje());
        return cobrancaRepository.somarRecebido(Cobranca.Status.PAGA, mes.atDay(1), mes.atEndOfMonth());
    }

    /**
     * Lista geral de cobranças (tela "Cobranças"), com cliente, produto e
     * plano já carregados. Criteria porque todos os filtros são opcionais.
     */
    @Transactional(readOnly = true)
    public List<CobrancaLinha> listar(FiltroCobrancas filtro) {
        LocalDate hoje = hoje();
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Cobranca> query = cb.createQuery(Cobranca.class);
        Root<Cobranca> cobranca = query.from(Cobranca.class);
        @SuppressWarnings("unchecked")
        Join<Cobranca, Contratacao> contratacao = (Join<Cobranca, Contratacao>) cobranca.<Cobranca, Contratacao>fetch("contratacao");
        @SuppressWarnings("unchecked")
        Join<Contratacao, Cliente> cliente = (Join<Contratacao, Cliente>) contratacao.<Contratacao, Cliente>fetch("cliente");
        @SuppressWarnings("unchecked")
        Join<Contratacao, Produto> produto = (Join<Contratacao, Produto>) contratacao.<Contratacao, Produto>fetch("produto");
        contratacao.fetch("plano");

        List<Predicate> filtros = new ArrayList<>();
        if (filtro.produtoId() != null) {
            filtros.add(cb.equal(produto.get("id"), filtro.produtoId()));
        }
        String situacao = texto(filtro.situacao());
        if (situacao != null) {
            if ("VENCIDA".equalsIgnoreCase(situacao)) {
                filtros.add(cb.equal(cobranca.get("status"), Cobranca.Status.ABERTA));
                filtros.add(cb.lessThan(cobranca.get("vencimento"), hoje));
            } else {
                filtros.add(cb.equal(cobranca.get("status"), status(situacao)));
            }
        }
        if (filtro.formaPagamento() != null) {
            filtros.add(cb.equal(cobranca.get("formaPagamento"), filtro.formaPagamento()));
        }
        if (filtro.vencimentoDe() != null) {
            filtros.add(cb.greaterThanOrEqualTo(cobranca.get("vencimento"), filtro.vencimentoDe()));
        }
        if (filtro.vencimentoAte() != null) {
            filtros.add(cb.lessThanOrEqualTo(cobranca.get("vencimento"), filtro.vencimentoAte()));
        }
        if (filtro.competencia() != null) {
            filtros.add(cb.lessThanOrEqualTo(cobranca.get("competenciaInicio"), filtro.competencia().atEndOfMonth()));
            filtros.add(cb.greaterThanOrEqualTo(cobranca.get("competenciaFim"), filtro.competencia().atDay(1)));
        }
        String busca = texto(filtro.busca());
        if (busca != null) {
            String padrao = "%" + busca.toLowerCase(Locale.ROOT) + "%";
            filtros.add(cb.or(
                    cb.like(cb.lower(cliente.get("nome")), padrao),
                    cb.like(cb.lower(cliente.get("documento")), padrao),
                    cb.like(cb.lower(contratacao.get("nomeInstancia")), padrao)));
        }
        query.select(cobranca).where(filtros.toArray(Predicate[]::new))
                .orderBy(cb.asc(cobranca.get("vencimento")), cb.asc(cliente.get("nome")));
        return entityManager.createQuery(query).setMaxResults(MAX_LINHAS_LISTA).getResultList().stream()
                .map(item -> CobrancaLinha.de(item, hoje))
                .toList();
    }

    @Transactional(readOnly = true)
    public CobrancaDetalhe detalhe(UUID cobrancaId) {
        Cobranca cobranca = cobrancaRepository.findById(cobrancaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cobrança não encontrada."));
        carregar(cobranca.getContratacaoId());
        return new CobrancaDetalhe(
                CobrancaLinha.de(cobranca, hoje()),
                cobranca.getObservacao(),
                cobranca.getItens().stream().map(CobrancaItemResponse::de).toList());
    }

    @Transactional
    public int gerarCobrancas(Contratacao contratacao, LocalDate ate) {
        return gerarCobrancas(contratacao, null, ate);
    }

    /**
     * Cria as cobranças que faltam com competência começando entre
     * {@code de} (vazio = início do contrato) e {@code ate}.
     */
    @Transactional
    public int gerarCobrancas(Contratacao contratacao, LocalDate de, LocalDate ate) {
        Set<LocalDate> existentes = new HashSet<>();
        for (Cobranca cobranca : cobrancaRepository.findByContratacaoId(contratacao.getId())) {
            existentes.add(cobranca.getCompetenciaInicio());
        }
        int meses = contratacao.getPeriodicidade().meses();
        LocalDate primeiro = contratacao.getInicio().withDayOfMonth(1);
        List<ContratacaoAdicional> adicionais = null;
        List<Cobranca> novas = new ArrayList<>();
        for (int i = 0; ; i++) {
            LocalDate inicioPeriodo = primeiro.plusMonths((long) i * meses);
            if (inicioPeriodo.isAfter(ate)) {
                break;
            }
            if (existentes.contains(inicioPeriodo) || (de != null && inicioPeriodo.isBefore(de))) {
                continue;
            }
            LocalDate fimPeriodo = primeiro.plusMonths((long) (i + 1) * meses).minusDays(1);
            LocalDate vencimento = inicioPeriodo.withDayOfMonth(contratacao.getDiaVencimento());
            if (vencimento.isBefore(contratacao.getInicio())) {
                vencimento = contratacao.getInicio();
            }
            if (adicionais == null) {
                adicionais = contratacaoAdicionalRepository.listarDaContratacao(contratacao.getId());
            }
            novas.add(new Cobranca(contratacao, inicioPeriodo, fimPeriodo, vencimento, itensDe(contratacao, adicionais)));
        }
        cobrancaRepository.saveAll(novas);
        return novas.size();
    }

    /**
     * Refaz os itens das cobranças abertas que ainda não venceram (decisão de
     * 26/09/2026): a vencida fica como estava, porque o cliente já devia aquilo.
     */
    @Transactional
    public void recalcularAbertasNaoVencidas(Contratacao contratacao) {
        LocalDate hoje = hoje();
        List<ContratacaoAdicional> adicionais = contratacaoAdicionalRepository.listarDaContratacao(contratacao.getId());
        for (Cobranca cobranca : cobrancaRepository.findByContratacaoId(contratacao.getId())) {
            if (cobranca.getStatus() == Cobranca.Status.ABERTA && !cobranca.getVencimento().isBefore(hoje)) {
                cobranca.definirItens(itensDe(contratacao, adicionais));
            }
        }
        entityManager.flush();
    }

    @Transactional
    public void removerAbertasAPartirDe(UUID contratacaoId, LocalDate data) {
        List<Cobranca> apagar = new ArrayList<>();
        for (Cobranca cobranca : cobrancaRepository.findByContratacaoId(contratacaoId)) {
            if (cobranca.getStatus() == Cobranca.Status.ABERTA && !cobranca.getCompetenciaInicio().isBefore(data)) {
                apagar.add(cobranca);
            }
        }
        cobrancaRepository.deleteAll(apagar);
        entityManager.flush();
    }

    @Transactional
    public void cancelarAbertasAPartirDe(UUID contratacaoId, LocalDate data, String motivo) {
        for (Cobranca cobranca : cobrancaRepository.findByContratacaoId(contratacaoId)) {
            if (cobranca.getStatus() == Cobranca.Status.ABERTA && !cobranca.getCompetenciaInicio().isBefore(data)) {
                cobranca.cancelar(motivo);
            }
        }
    }

    /** Plano do período mais cada adicional contratado. */
    private static List<CobrancaItem> itensDe(Contratacao contratacao, List<ContratacaoAdicional> adicionais) {
        Periodicidade periodicidade = contratacao.getPeriodicidade();
        List<CobrancaItem> itens = new ArrayList<>();
        itens.add(CobrancaItem.plano(
                "Plano " + contratacao.getPlano().getNome() + " (" + rotulo(periodicidade) + ")",
                contratacao.getValor()));
        for (ContratacaoAdicional item : adicionais) {
            itens.add(CobrancaItem.adicional(
                    "Adicional " + item.getAdicional().getNome(),
                    item.getQuantidade(), item.getAdicional().getPreco(), periodicidade.meses()));
        }
        return itens;
    }

    private void pagar(Contratacao contratacao, List<Cobranca> cobrancas, RegistrarPagamentoRequest request) {
        for (Cobranca cobranca : cobrancas) {
            if (cobranca.getStatus() != Cobranca.Status.ABERTA) {
                throw new BadRequestException("A cobrança de " + competencia(cobranca) + " não está em aberto.");
            }
        }
        String observacao = texto(request.observacao());
        LocalDate fimMaisDistante = null;
        for (Cobranca cobranca : cobrancas) {
            BigDecimal valorPago = request.valorPago() != null ? request.valorPago() : cobranca.getValor();
            cobranca.pagar(request.pagoEm(), valorPago, request.formaPagamento(), observacao, operadorAtualId());
            if (fimMaisDistante == null || cobranca.getCompetenciaFim().isAfter(fimMaisDistante)) {
                fimMaisDistante = cobranca.getCompetenciaFim();
            }
        }
        entityManager.flush();

        SituacaoComercial antes = contratacao.getSituacaoComercial();
        LocalDate vigenteAntes = contratacao.getVigenteAte();
        if (fimMaisDistante != null && (vigenteAntes == null || fimMaisDistante.isAfter(vigenteAntes))) {
            contratacao.setVigenteAte(fimMaisDistante);
        }
        boolean aindaEmAtraso = cobrancaRepository.existsByContratacaoIdAndStatusAndVencimentoBefore(
                contratacao.getId(), Cobranca.Status.ABERTA, hoje());
        if (!aindaEmAtraso
                && (antes == SituacaoComercial.TRIAL || antes == SituacaoComercial.INADIMPLENTE)) {
            contratacao.setSituacaoComercial(SituacaoComercial.ATIVA);
        }
        if (antes != contratacao.getSituacaoComercial()
                || (vigenteAntes == null ? contratacao.getVigenteAte() != null : !vigenteAntes.equals(contratacao.getVigenteAte()))) {
            direitos.publicar(contratacao, "PAGAMENTO", observacao);
        }
    }

    private List<UUID> validarPagamento(RegistrarPagamentoRequest request) {
        if (request.pagoEm().isAfter(hoje())) {
            throw new BadRequestException("A data do pagamento não pode estar no futuro.");
        }
        List<UUID> ids = List.copyOf(new LinkedHashSet<>(request.cobrancaIds()));
        if (request.valorPago() != null && ids.size() > 1) {
            throw new BadRequestException("Valor pago só pode ser informado para uma cobrança por vez.");
        }
        return ids;
    }

    private FinanceiroResponse montar(UUID contratacaoId) {
        LocalDate hoje = hoje();
        List<Cobranca> cobrancas = cobrancaRepository.findByContratacaoIdOrderByCompetenciaInicioDesc(contratacaoId);
        List<Cobranca> vencidas = cobrancas.stream().filter(cobranca -> cobranca.vencidaEm(hoje)).toList();
        long diasAtraso = vencidas.stream().map(Cobranca::getVencimento).min(Comparator.naturalOrder())
                .map(vencimento -> ChronoUnit.DAYS.between(vencimento, hoje)).orElse(0L);
        BigDecimal valorEmAtraso = vencidas.stream().map(Cobranca::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate proximoVencimento = cobrancas.stream()
                .filter(cobranca -> cobranca.getStatus() == Cobranca.Status.ABERTA && !cobranca.getVencimento().isBefore(hoje))
                .map(Cobranca::getVencimento).min(Comparator.naturalOrder()).orElse(null);
        BigDecimal totalPagoNoAno = cobrancas.stream()
                .filter(cobranca -> cobranca.getStatus() == Cobranca.Status.PAGA
                        && cobranca.getPagoEm().getYear() == hoje.getYear())
                .map(Cobranca::getValorPago).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FinanceiroResponse(
                cobrancas.stream().map(cobranca -> CobrancaResponse.de(cobranca, hoje)).toList(),
                new Resumo(vencidas.size(), diasAtraso, valorEmAtraso, proximoVencimento, totalPagoNoAno));
    }

    private List<Cobranca> buscarCobrancas(UUID contratacaoId, List<UUID> ids) {
        List<Cobranca> cobrancas = cobrancaRepository.findByIdInAndContratacaoId(ids, contratacaoId);
        if (cobrancas.size() != ids.size()) {
            throw new ResourceNotFoundException("Cobrança não encontrada.");
        }
        return cobrancas;
    }

    private Contratacao carregar(UUID id) {
        Contratacao contratacao = contratacaoRepository.buscarComReferencias(id);
        if (contratacao == null) {
            throw new ResourceNotFoundException("Contratação não encontrada.");
        }
        return contratacao;
    }

    private static void exigirNaoCancelada(Contratacao contratacao) {
        if (contratacao.getSituacaoComercial() == SituacaoComercial.CANCELADA) {
            throw new BadRequestException("Contratação cancelada não recebe movimentação financeira.");
        }
    }

    private static Cobranca.Status status(String situacao) {
        try {
            return Cobranca.Status.valueOf(situacao.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Situação de cobrança inválida.");
        }
    }

    private static String rotulo(Periodicidade periodicidade) {
        return switch (periodicidade) {
            case MENSAL -> "mensal";
            case TRIMESTRAL -> "trimestral";
            case SEMESTRAL -> "semestral";
            case ANUAL -> "anual";
        };
    }

    private static String competencia(Cobranca cobranca) {
        return cobranca.getCompetenciaInicio().format(MES_ANO);
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static UUID operadorAtualId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof OperadorAutenticado operador) {
            return operador.id();
        }
        return null;
    }
}
