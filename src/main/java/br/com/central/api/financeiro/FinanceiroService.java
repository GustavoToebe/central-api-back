package br.com.central.api.financeiro;

import br.com.central.api.web.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import static br.com.central.api.financeiro.FinanceiroDtos.*;
import static br.com.central.api.financeiro.MovimentoFinanceiro.*;

@Service @Transactional(readOnly=true)
public class FinanceiroService {
    private final ContaFinanceiraRepository contas;
    private final CategoriaFinanceiraRepository categorias;
    private final MovimentoFinanceiroRepository movimentos;
    private final AuditoriaFinanceira audit;
    private final br.com.central.api.comercial.CobrancaRepository cobrancas;
    public FinanceiroService(ContaFinanceiraRepository contas, CategoriaFinanceiraRepository categorias,
                             MovimentoFinanceiroRepository movimentos, AuditoriaFinanceira audit, br.com.central.api.comercial.CobrancaRepository cobrancas) {
        this.contas=contas; this.categorias=categorias; this.movimentos=movimentos; this.audit=audit;
        this.cobrancas=cobrancas;
    }
    public List<ContaResponse> contas() { return contas.findAllByOrderByNomeAsc().stream().map(ContaResponse::de).toList(); }
    public List<CategoriaResponse> categorias() { return categorias.findAllByOrderByNomeAsc().stream().map(CategoriaResponse::de).toList(); }
    @Transactional public ContaResponse salvarConta(UUID id, ContaRequest req) {
        ContaFinanceira c = id==null ? new ContaFinanceira(req.nome().trim()) : contaParaAlterar(id);
        if (req.dataSaldoInicial().isBefore(LocalDate.of(1900,1,1)) || req.dataSaldoInicial().isAfter(hoje())) throw new BadRequestException("A data do saldo inicial deve ser entre 1900 e hoje.");
        if (id!=null && movimentos.existsByConta_Id(id) && (c.getSaldoInicial().compareTo(req.saldoInicial())!=0 || !c.getDataSaldoInicial().equals(req.dataSaldoInicial())))
            throw new ConflictException("CONFLITO_FINANCEIRO", "O saldo inicial não pode mudar depois de existir lançamento nesta conta.");
        if ((id==null || !c.getNome().equalsIgnoreCase(req.nome().trim())) && contas.existsByNomeIgnoreCase(req.nome().trim()))
            throw new ConflictException("CONFLITO_FINANCEIRO", "Já existe uma conta com este nome.");
        c.setNome(req.nome().trim()); c.setSaldoInicial(req.saldoInicial()); c.setDataSaldoInicial(req.dataSaldoInicial()); c.setAtivo(req.ativo());
        contas.saveAndFlush(c); audit.registrar(id==null ? "CRIACAO" : "ALTERACAO", "FINANCEIRO_CONTA", c.getId(), List.of("configuracao"));
        return ContaResponse.de(c);
    }
    @Transactional public CategoriaResponse salvarCategoria(UUID id, CategoriaRequest req) {
        CategoriaFinanceira c = id==null ? new CategoriaFinanceira(req.nome().trim()) : categoria(id);
        if ((id==null || !c.getNome().equalsIgnoreCase(req.nome().trim())) && categorias.existsByNomeIgnoreCase(req.nome().trim()))
            throw new ConflictException("CONFLITO_FINANCEIRO", "Já existe uma categoria com este nome.");
        c.setNome(req.nome().trim()); c.setAtivo(req.ativo()); categorias.saveAndFlush(c);
        audit.registrar(id==null ? "CRIACAO" : "ALTERACAO", "FINANCEIRO_CATEGORIA", c.getId(), List.of("configuracao"));
        return CategoriaResponse.de(c);
    }
    public Pagina listar(LocalDate de, LocalDate ate, String nome, UUID contaId, UUID categoriaId, Situacao situacao, Tipo tipo, int pagina, int tamanho) {
        intervalo(de,ate);
        if (pagina<0 || tamanho<1 || tamanho>100) throw new BadRequestException("Página inválida; tamanho de 1 a 100.");
        Specification<MovimentoFinanceiro> filtro = (root,q,cb) -> {
            var p = new ArrayList<jakarta.persistence.criteria.Predicate>();
            p.add(cb.between(root.get("vencimento"),de,ate));
            if (nome!=null && !nome.isBlank()) p.add(cb.like(cb.lower(root.get("descricao")),"%"+nome.trim().toLowerCase(Locale.ROOT)+"%"));
            if (contaId!=null) p.add(cb.equal(root.get("conta").get("id"),contaId));
            if (categoriaId!=null) p.add(cb.equal(root.get("categoria").get("id"),categoriaId));
            if (situacao!=null) p.add(cb.equal(root.get("situacao"),situacao));
            if (tipo!=null) p.add(cb.equal(root.get("tipo"),tipo));
            return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var result = movimentos.findAll(filtro,PageRequest.of(pagina,tamanho,Sort.by("vencimento").descending().and(Sort.by("id"))));
        return new Pagina(result.stream().map(MovimentoResponse::de).toList(),result.getTotalElements(),pagina,tamanho);
    }
    @Transactional public MovimentoResponse salvarMovimento(UUID id, MovimentoRequest req) {
        MovimentoFinanceiro m = id==null ? new MovimentoFinanceiro(req.descricao().trim()) : alterar(id,req.versao());
        if (m.getSituacao()!=Situacao.PENDENTE) throw new ConflictException("CONFLITO_FINANCEIRO", "Somente lançamentos pendentes podem ser editados.");
        if (req.vencimento().isBefore(LocalDate.of(1900,1,1))) throw new BadRequestException("Informe vencimento a partir de 1900.");
        ContaFinanceira c = contaParaAlterar(req.contaId()); CategoriaFinanceira cat = categoria(req.categoriaId());
        if (!c.isAtivo() || !cat.isAtivo()) throw new BadRequestException("Escolha conta e categoria ativas.");
        m.setDescricao(req.descricao().trim()); m.setTipo(req.tipo()); m.setValor(req.valor()); m.setVencimento(req.vencimento());
        m.setConta(c); m.setCategoria(cat); m.setObservacoes(req.observacoes()==null ? null : req.observacoes().trim());
        movimentos.saveAndFlush(m); audit.registrar(id==null ? "CRIACAO" : "ALTERACAO", "FINANCEIRO_MOVIMENTO",m.getId(),List.of("lancamento"));
        return MovimentoResponse.de(m);
    }
    @Transactional public MovimentoResponse baixar(UUID id, BaixaRequest req) {
        MovimentoFinanceiro m=alterar(id,req.versao());
        if (m.getSituacao()!=Situacao.PENDENTE) throw new ConflictException("CONFLITO_FINANCEIRO", "Este lançamento não está pendente.");
        if (req.dataPagamento().isAfter(hoje()) || req.dataPagamento().isBefore(m.getConta().getDataSaldoInicial()))
            throw new BadRequestException("A baixa deve ser entre a data do saldo inicial e hoje.");
        m.setSituacao(Situacao.PAGO); m.setDataPagamento(req.dataPagamento());
        movimentos.flush(); audit.registrar("BAIXA", "FINANCEIRO_MOVIMENTO",id,List.of("situacao","dataPagamento"));
        return MovimentoResponse.de(m);
    }
    @Transactional public MovimentoResponse estornar(UUID id, VersaoRequest req) {
        MovimentoFinanceiro m=alterar(id,req.versao());
        if (m.getSituacao()!=Situacao.PAGO) throw new ConflictException("CONFLITO_FINANCEIRO", "Somente lançamentos pagos podem ser estornados.");
        m.setSituacao(Situacao.PENDENTE); m.setDataPagamento(null); movimentos.flush();
        audit.registrar("ESTORNO", "FINANCEIRO_MOVIMENTO",id,List.of("situacao","dataPagamento")); return MovimentoResponse.de(m);
    }
    @Transactional public MovimentoResponse cancelar(UUID id, VersaoRequest req) {
        MovimentoFinanceiro m=alterar(id,req.versao());
        if (m.getSituacao()!=Situacao.PENDENTE) throw new ConflictException("CONFLITO_FINANCEIRO", "Estorne a baixa antes de cancelar o lançamento.");
        m.setSituacao(Situacao.CANCELADO); movimentos.flush();
        audit.registrar("CANCELAMENTO", "FINANCEIRO_MOVIMENTO",id,List.of("situacao")); return MovimentoResponse.de(m);
    }
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Resumo resumo(LocalDate de, LocalDate ate) {
        intervalo(de,ate);
        BigDecimal receitas=BigDecimal.ZERO, despesas=BigDecimal.ZERO, saldoTotal=BigDecimal.ZERO;
        for (var total : movimentos.totais(de,ate)) { receitas=receitas.add(total.getReceitas()); despesas=despesas.add(total.getDespesas()); }
        Map<UUID,BigDecimal> acumulado=new HashMap<>();
        for (var total : movimentos.totais(LocalDate.of(1900,1,1),ate)) acumulado.put(total.getContaId(),total.getReceitas().subtract(total.getDespesas()));
        List<SaldoConta> saldos=new ArrayList<>();
        for (var c : contas.findAllByOrderByNomeAsc()) {
            BigDecimal saldo = c.getDataSaldoInicial().isAfter(ate) ? BigDecimal.ZERO : c.getSaldoInicial().add(acumulado.getOrDefault(c.getId(),BigDecimal.ZERO));
            saldos.add(new SaldoConta(c.getId(),c.getNome(),saldo)); saldoTotal=saldoTotal.add(saldo);
        }
        BigDecimal manuais=receitas;
        BigDecimal comerciais=cobrancas.somarRecebido(br.com.central.api.comercial.Cobranca.Status.PAGA,de,ate);
        receitas=receitas.add(comerciais);
        BigDecimal[] pendentes=movimentos.previstos(de,ate).getFirst();
        BigDecimal receber=pendentes[0].add(cobrancas.somarPrevisto(de,ate));
        BigDecimal pagar=pendentes[1];
        return new Resumo(de,ate,receitas,despesas,receitas.subtract(despesas),saldoTotal,saldos,
                comerciais,manuais,receber,pagar,receitas.add(receber).subtract(despesas).subtract(pagar));
    }
    private MovimentoFinanceiro alterar(UUID id,long versao) {
        var m=movimentos.buscarParaAlterar(id).orElseThrow(() -> new ResourceNotFoundException("Lançamento não encontrado."));
        if (m.getVersao()!=versao) throw new ConflictException("CONFLITO_FINANCEIRO", "Lançamento alterado por outra pessoa. Atualize a página.");
        return m;
    }
    private ContaFinanceira contaParaAlterar(UUID id) { return contas.buscarParaAlterar(id).orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada.")); }
    private ContaFinanceira conta(UUID id) { return contas.findById(id).orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada.")); }
    private CategoriaFinanceira categoria(UUID id) { return categorias.findById(id).orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada.")); }
    private static LocalDate hoje() { return LocalDate.now(ZoneId.of("America/Sao_Paulo")); }
    private static void intervalo(LocalDate de, LocalDate ate) { if (de==null || ate==null || de.isAfter(ate) || de.isBefore(LocalDate.of(1900,1,1))) throw new BadRequestException("Informe um período válido, a partir de 1900."); }
}
