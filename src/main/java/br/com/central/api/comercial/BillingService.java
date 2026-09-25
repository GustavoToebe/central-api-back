package br.com.central.api.comercial;

import br.com.central.api.comercial.dto.FinanceiroDtos.CobrancaResponse;
import br.com.central.api.comercial.dto.FinanceiroDtos.FinanceiroResponse;
import br.com.central.api.comercial.dto.FinanceiroDtos.RegistrarPagamentoRequest;
import br.com.central.api.comercial.dto.FinanceiroDtos.Resumo;
import br.com.central.api.comercial.dto.FinanceiroDtos.SituacaoFinanceira;
import br.com.central.api.security.OperadorAutenticado;
import br.com.central.api.web.BadRequestException;
import br.com.central.api.web.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cobrança manual por contratação, adaptada do billing do Servire.
 * O job diário gera cobranças e marca INADIMPLENTE. Bloqueio continua manual.
 */
@Service
public class BillingService {

    static final int MESES_ANTECEDENCIA = 1;
    static final int MAX_MESES_ADIANTADOS = 36;

    private static final DateTimeFormatter MES_ANO = DateTimeFormatter.ofPattern("MM/yyyy");

    private final CobrancaRepository cobrancaRepository;
    private final ContratacaoRepository contratacaoRepository;
    private final DireitosDaContratacao direitos;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public BillingService(CobrancaRepository cobrancaRepository, ContratacaoRepository contratacaoRepository,
                          DireitosDaContratacao direitos, Clock clock) {
        this.cobrancaRepository = cobrancaRepository;
        this.contratacaoRepository = contratacaoRepository;
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

    @Transactional
    public FinanceiroResponse gerarAdiantadas(UUID contratacaoId, YearMonth ate) {
        Contratacao contratacao = carregar(contratacaoId);
        exigirNaoCancelada(contratacao);
        YearMonth limite = YearMonth.from(hoje()).plusMonths(MAX_MESES_ADIANTADOS);
        if (ate.isAfter(limite)) {
            throw new BadRequestException("Gere no máximo " + MAX_MESES_ADIANTADOS + " meses à frente.");
        }
        gerarCobrancas(contratacao, ate.atEndOfMonth());
        return montar(contratacaoId);
    }

    @Transactional
    public FinanceiroResponse registrarPagamento(UUID contratacaoId, RegistrarPagamentoRequest request) {
        Contratacao contratacao = carregar(contratacaoId);
        exigirNaoCancelada(contratacao);
        LocalDate hoje = hoje();
        if (request.pagoEm().isAfter(hoje)) {
            throw new BadRequestException("A data do pagamento não pode estar no futuro.");
        }
        List<UUID> ids = List.copyOf(new LinkedHashSet<>(request.cobrancaIds()));
        if (request.valorPago() != null && ids.size() > 1) {
            throw new BadRequestException("Valor pago só pode ser informado para uma cobrança por vez.");
        }
        List<Cobranca> cobrancas = buscarCobrancas(contratacaoId, ids);
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
                contratacaoId, Cobranca.Status.ABERTA, hoje);
        if (!aindaEmAtraso
                && (antes == SituacaoComercial.TRIAL || antes == SituacaoComercial.INADIMPLENTE)) {
            contratacao.setSituacaoComercial(SituacaoComercial.ATIVA);
        }
        if (antes != contratacao.getSituacaoComercial()
                || (vigenteAntes == null ? contratacao.getVigenteAte() != null : !vigenteAntes.equals(contratacao.getVigenteAte()))) {
            direitos.publicar(contratacao, "PAGAMENTO", observacao);
        }
        return montar(contratacaoId);
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

    @Transactional
    public int gerarCobrancas(Contratacao contratacao, LocalDate ate) {
        Set<LocalDate> existentes = new HashSet<>();
        for (Cobranca cobranca : cobrancaRepository.findByContratacaoId(contratacao.getId())) {
            existentes.add(cobranca.getCompetenciaInicio());
        }
        LocalDate limite = ate;
        int meses = contratacao.getPeriodicidade().meses();
        LocalDate primeiro = contratacao.getPeriodicidade() == Periodicidade.MENSAL
                ? contratacao.getInicio().withDayOfMonth(1)
                : contratacao.getInicio();
        List<Cobranca> novas = new ArrayList<>();
        for (int i = 0; ; i++) {
            LocalDate inicioPeriodo = primeiro.plusMonths((long) i * meses);
            if (inicioPeriodo.isAfter(limite)) {
                break;
            }
            if (existentes.contains(inicioPeriodo)) {
                continue;
            }
            LocalDate fimPeriodo = primeiro.plusMonths((long) (i + 1) * meses).minusDays(1);
            LocalDate vencimento = inicioPeriodo.withDayOfMonth(contratacao.getDiaVencimento());
            if (vencimento.isBefore(contratacao.getInicio())) {
                vencimento = contratacao.getInicio();
            }
            novas.add(new Cobranca(contratacao, inicioPeriodo, fimPeriodo, vencimento));
        }
        cobrancaRepository.saveAll(novas);
        return novas.size();
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
