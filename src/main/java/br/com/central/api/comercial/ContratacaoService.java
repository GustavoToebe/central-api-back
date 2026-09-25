package br.com.central.api.comercial;

import br.com.central.api.comercial.dto.ContratacaoDtos.AdicionalContratadoRequest;
import br.com.central.api.comercial.dto.ContratacaoDtos.AdicionalContratadoResponse;
import br.com.central.api.comercial.dto.ContratacaoDtos.AlterarPlanoRequest;
import br.com.central.api.comercial.dto.ContratacaoDtos.AtualizarProvisionamentoRequest;
import br.com.central.api.comercial.dto.ContratacaoDtos.ContratacaoResponse;
import br.com.central.api.comercial.dto.ContratacaoDtos.ContratacaoResumo;
import br.com.central.api.comercial.dto.ContratacaoDtos.CriarContratacaoRequest;
import br.com.central.api.comercial.dto.ContratacaoDtos.HistoricoResponse;
import br.com.central.api.comercial.dto.ContratacaoDtos.SubstituirAdicionaisRequest;
import br.com.central.api.web.BadRequestException;
import br.com.central.api.web.ConflictException;
import br.com.central.api.web.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ContratacaoService {

    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    private final ContratacaoRepository contratacaoRepository;
    private final ContratacaoAdicionalRepository contratacaoAdicionalRepository;
    private final HistoricoContratacaoRepository historicoRepository;
    private final ClienteService clienteService;
    private final CatalogoService catalogoService;
    private final DireitosDaContratacao direitos;
    private final BillingService billingService;
    private final CobrancaRepository cobrancaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public ContratacaoService(ContratacaoRepository contratacaoRepository,
                              ContratacaoAdicionalRepository contratacaoAdicionalRepository,
                              HistoricoContratacaoRepository historicoRepository,
                              ClienteService clienteService,
                              CatalogoService catalogoService,
                              DireitosDaContratacao direitos,
                              BillingService billingService,
                              CobrancaRepository cobrancaRepository) {
        this.contratacaoRepository = contratacaoRepository;
        this.contratacaoAdicionalRepository = contratacaoAdicionalRepository;
        this.historicoRepository = historicoRepository;
        this.clienteService = clienteService;
        this.catalogoService = catalogoService;
        this.direitos = direitos;
        this.billingService = billingService;
        this.cobrancaRepository = cobrancaRepository;
    }

    @Transactional(readOnly = true)
    public List<ContratacaoResumo> listar() {
        return contratacaoRepository.findAllByOrderByCriadoEmDesc().stream().map(this::resumo).toList();
    }

    @Transactional(readOnly = true)
    public ContratacaoResponse buscar(UUID id) {
        return detalhe(carregar(id));
    }

    @Transactional
    public ContratacaoResponse criar(CriarContratacaoRequest request) {
        Cliente cliente = clienteService.carregar(request.clienteId());
        Produto produto = catalogoService.carregarProduto(request.produtoId());
        Plano plano = planoDoProduto(request.planoId(), produto.getId());
        if (!plano.isAtivo()) {
            throw new BadRequestException("Plano inativo não pode ser contratado.");
        }
        SituacaoComercial situacao = request.situacaoComercial() == null
                ? SituacaoComercial.TRIAL : request.situacaoComercial();
        if (situacao != SituacaoComercial.TRIAL && situacao != SituacaoComercial.ATIVA) {
            throw new BadRequestException("A contratação nasce em trial ou ativa.");
        }
        BigDecimal valor = valorOuPreco(plano.getId(), request.periodicidade(), request.valor(), billingService.hoje());
        String slug = slug(request.slugInstancia());
        if (contratacaoRepository.existsByProduto_IdAndSlugInstancia(produto.getId(), slug)) {
            throw new ConflictException("Já existe instância com este slug neste produto.", "CONFLITO");
        }
        Contratacao contratacao = new Contratacao(
                cliente, produto, plano, request.periodicidade(), valor, request.diaVencimento(), request.inicio(),
                situacao, request.nomeInstancia().trim(), slug,
                request.adminNome().trim(), request.adminEmail().trim().toLowerCase());
        contratacao.setObservacoes(texto(request.observacoes()));
        contratacao.setVersaoDireitos(1);
        contratacao.setDireitosAtuais("{}");
        contratacao = contratacaoRepository.saveAndFlush(contratacao);
        gravarAdicionais(contratacao, request.adicionais());
        direitos.gravarVersaoAtual(contratacao, "CRIAR", null);
        billingService.gerarCobrancas(contratacao, billingService.hoje().plusMonths(BillingService.MESES_ANTECEDENCIA));
        return detalhe(contratacao);
    }

    @Transactional
    public ContratacaoResponse atualizarProvisionamento(UUID id, AtualizarProvisionamentoRequest request) {
        Contratacao contratacao = carregar(id);
        exigirNaoCancelada(contratacao);
        String slug = slug(request.slugInstancia());
        boolean mudou = !contratacao.getNomeInstancia().equals(request.nomeInstancia().trim())
                || !contratacao.getSlugInstancia().equals(slug)
                || !contratacao.getAdminNome().equals(request.adminNome().trim())
                || !contratacao.getAdminEmail().equals(request.adminEmail().trim().toLowerCase());
        if (!mudou) {
            return detalhe(contratacao);
        }
        if (!contratacao.dadosDeProvisionamentoEditaveis()) {
            throw new ConflictException(contratacao.getIdExterno() != null
                    ? "A instância já foi criada no aplicativo; nome, slug e administrador não mudam mais por aqui."
                    : "O aplicativo pode já ter criado a instância com os dados atuais; tente o provisionamento de novo antes de editar.",
                    "PROVISIONAMENTO_NAO_EDITAVEL");
        }
        if (!contratacao.getSlugInstancia().equals(slug)
                && contratacaoRepository.existsByProduto_IdAndSlugInstancia(contratacao.getProduto().getId(), slug)) {
            throw new ConflictException("Já existe instância com este slug neste produto.", "CONFLITO");
        }
        contratacao.setNomeInstancia(request.nomeInstancia().trim());
        contratacao.setSlugInstancia(slug);
        contratacao.setAdminNome(request.adminNome().trim());
        contratacao.setAdminEmail(request.adminEmail().trim().toLowerCase());
        if (contratacao.getSituacaoProvisionamento() == SituacaoProvisionamento.ERRO) {
            contratacao.setSituacaoProvisionamento(SituacaoProvisionamento.PENDENTE);
        }
        direitos.publicar(contratacao, "DADOS_PROVISIONAMENTO", null);
        return detalhe(contratacao);
    }

    @Transactional
    public ContratacaoResponse alterarPlano(UUID id, AlterarPlanoRequest request) {
        Contratacao contratacao = carregar(id);
        exigirNaoCancelada(contratacao);
        Plano plano = planoDoProduto(request.planoId(), contratacao.getProduto().getId());
        if (!plano.isAtivo()) {
            throw new BadRequestException("Plano inativo não pode ser contratado.");
        }
        if (request.aPartirDe().isBefore(contratacao.getInicio())) {
            throw new BadRequestException("A troca de plano não pode começar antes do contrato.");
        }
        BigDecimal valor = valorOuPreco(plano.getId(), request.periodicidade(), request.valor(), billingService.hoje());
        billingService.removerAbertasAPartirDe(contratacao.getId(), request.aPartirDe());
        contratacao.setPlano(plano);
        contratacao.setPeriodicidade(request.periodicidade());
        contratacao.setValor(valor);
        contratacao.setDiaVencimento(request.diaVencimento());
        direitos.publicar(contratacao, "TROCA_PLANO", request.motivo());
        billingService.gerarCobrancas(contratacao, billingService.hoje().plusMonths(BillingService.MESES_ANTECEDENCIA));
        return detalhe(contratacao);
    }

    @Transactional
    public ContratacaoResponse substituirAdicionais(UUID id, SubstituirAdicionaisRequest request) {
        Contratacao contratacao = carregar(id);
        exigirNaoCancelada(contratacao);
        contratacaoAdicionalRepository.deleteByContratacao_Id(id);
        entityManager.flush();
        gravarAdicionais(contratacao, request.adicionais());
        direitos.publicar(contratacao, "ADICIONAIS", request.motivo());
        return detalhe(contratacao);
    }

    @Transactional
    public ContratacaoResponse bloquear(UUID id, String motivo) {
        Contratacao contratacao = carregar(id);
        if (contratacao.getSituacaoComercial() == SituacaoComercial.CANCELADA) {
            throw new BadRequestException("Contratação cancelada não pode ser bloqueada.");
        }
        if (contratacao.getSituacaoComercial() == SituacaoComercial.BLOQUEADA) {
            throw new BadRequestException("Contratação já está bloqueada.");
        }
        String texto = texto(motivo);
        if (texto == null) {
            throw new BadRequestException("Informe o motivo do bloqueio.");
        }
        contratacao.setSituacaoComercial(SituacaoComercial.BLOQUEADA);
        contratacao.setMotivoBloqueio(texto);
        direitos.publicar(contratacao, "BLOQUEAR", texto);
        return detalhe(contratacao);
    }

    @Transactional
    public ContratacaoResponse desbloquear(UUID id, String motivo) {
        Contratacao contratacao = carregar(id);
        if (contratacao.getSituacaoComercial() != SituacaoComercial.BLOQUEADA) {
            throw new BadRequestException("Só é possível desbloquear uma contratação bloqueada.");
        }
        boolean atraso = cobrancaRepository.existsByContratacaoIdAndStatusAndVencimentoBefore(
                id, Cobranca.Status.ABERTA, billingService.hoje());
        contratacao.setSituacaoComercial(atraso ? SituacaoComercial.INADIMPLENTE : SituacaoComercial.ATIVA);
        contratacao.setMotivoBloqueio(null);
        direitos.publicar(contratacao, "DESBLOQUEAR", motivo);
        return detalhe(contratacao);
    }

    @Transactional
    public ContratacaoResponse cancelar(UUID id, String motivo) {
        Contratacao contratacao = carregar(id);
        if (contratacao.getSituacaoComercial() == SituacaoComercial.CANCELADA) {
            throw new BadRequestException("Contratação já está cancelada.");
        }
        contratacao.setSituacaoComercial(SituacaoComercial.CANCELADA);
        contratacao.setMotivoBloqueio(null);
        billingService.cancelarAbertasAPartirDe(id, billingService.hoje().plusDays(1), "Contratação encerrada");
        direitos.publicar(contratacao, "CANCELAR", motivo);
        return detalhe(contratacao);
    }

    private void gravarAdicionais(Contratacao contratacao, List<AdicionalContratadoRequest> pedidos) {
        if (pedidos == null) {
            return;
        }
        Set<UUID> vistos = new HashSet<>();
        for (AdicionalContratadoRequest pedido : pedidos) {
            if (!vistos.add(pedido.adicionalId())) {
                throw new BadRequestException("Adicional repetido na contratação.");
            }
            Adicional adicional = catalogoService.carregarAdicional(pedido.adicionalId());
            if (!adicional.getProdutoId().equals(contratacao.getProduto().getId())) {
                throw new BadRequestException("O adicional não pertence a este produto.");
            }
            if (!adicional.isAtivo()) {
                throw new BadRequestException("Adicional inativo não pode ser contratado.");
            }
            contratacaoAdicionalRepository.save(new ContratacaoAdicional(contratacao, adicional, pedido.quantidade()));
        }
        entityManager.flush();
    }

    private BigDecimal valorOuPreco(UUID planoId, Periodicidade periodicidade, BigDecimal valor, LocalDate data) {
        if (valor != null) {
            return valor;
        }
        return catalogoService.precoVigente(planoId, periodicidade, data)
                .orElseThrow(() -> new BadRequestException(
                        "Plano sem preço cadastrado para esta periodicidade. Informe o valor."));
    }

    private Plano planoDoProduto(UUID planoId, UUID produtoId) {
        Plano plano = catalogoService.carregarPlano(planoId);
        if (!plano.getProdutoId().equals(produtoId)) {
            throw new BadRequestException("O plano não pertence a este produto.");
        }
        return plano;
    }

    private Contratacao carregar(UUID id) {
        Contratacao contratacao = contratacaoRepository.buscarComReferencias(id);
        if (contratacao == null) {
            throw new ResourceNotFoundException("Contratação não encontrada.");
        }
        return contratacao;
    }

    private ContratacaoResponse detalhe(Contratacao contratacao) {
        List<AdicionalContratadoResponse> adicionais = contratacaoAdicionalRepository
                .listarDaContratacao(contratacao.getId()).stream()
                .map(item -> new AdicionalContratadoResponse(
                        item.getAdicional().getId(),
                        item.getAdicional().getCodigo(),
                        item.getAdicional().getRecurso().getCodigo(),
                        item.getAdicional().getQuantidade(),
                        item.getQuantidade()))
                .toList();
        List<HistoricoResponse> historico = historicoRepository
                .findByContratacaoIdOrderByCriadoEmAsc(contratacao.getId()).stream()
                .map(item -> new HistoricoResponse(
                        item.getId(), item.getAcao(), item.getMotivo(), item.getOperadorId(),
                        item.getVersaoDireitos(), item.getCriadoEm()))
                .toList();
        return new ContratacaoResponse(
                contratacao.getId(),
                contratacao.getCliente().getId(),
                contratacao.getProduto().getId(),
                contratacao.getProduto().getCodigo(),
                contratacao.getPlano().getId(),
                contratacao.getPlano().getCodigo(),
                contratacao.getPlano().getNome(),
                contratacao.getPeriodicidade(),
                contratacao.getValor(),
                contratacao.getDiaVencimento(),
                contratacao.getInicio(),
                contratacao.getVigenteAte(),
                contratacao.getSituacaoComercial(),
                contratacao.getSituacaoProvisionamento(),
                contratacao.getIdempotencyKey(),
                contratacao.getIdExterno(),
                contratacao.dadosDeProvisionamentoEditaveis(),
                contratacao.getNomeInstancia(),
                contratacao.getSlugInstancia(),
                contratacao.getAdminNome(),
                contratacao.getAdminEmail(),
                contratacao.getVersaoDireitos(),
                direitos.ler(contratacao),
                adicionais,
                historico);
    }

    private ContratacaoResumo resumo(Contratacao contratacao) {
        return new ContratacaoResumo(
                contratacao.getId(),
                contratacao.getCliente().getId(),
                contratacao.getCliente().getNome(),
                contratacao.getProduto().getId(),
                contratacao.getProduto().getCodigo(),
                contratacao.getPlano().getId(),
                contratacao.getPlano().getCodigo(),
                contratacao.getPeriodicidade(),
                contratacao.getValor(),
                contratacao.getDiaVencimento(),
                contratacao.getVigenteAte(),
                contratacao.getSituacaoComercial(),
                contratacao.getSituacaoProvisionamento(),
                contratacao.getVersaoDireitos(),
                contratacao.getSituacaoComercial().isAcessoLiberado(),
                contratacao.getNomeInstancia(),
                contratacao.getSlugInstancia());
    }

    private static void exigirNaoCancelada(Contratacao contratacao) {
        if (contratacao.getSituacaoComercial() == SituacaoComercial.CANCELADA) {
            throw new BadRequestException("Contratação cancelada não pode ser alterada.");
        }
    }

    private static String slug(String valor) {
        String normalizado = valor.trim().toLowerCase();
        if (!SLUG.matcher(normalizado).matches()) {
            throw new BadRequestException("O slug só pode ter letras minúsculas, números e hífen.");
        }
        return normalizado;
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
