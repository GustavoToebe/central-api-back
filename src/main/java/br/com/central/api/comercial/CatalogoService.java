package br.com.central.api.comercial;

import br.com.central.api.comercial.dto.CatalogoDtos.AdicionalResponse;
import br.com.central.api.comercial.dto.CatalogoDtos.NovoPrecoRequest;
import br.com.central.api.comercial.dto.CatalogoDtos.PlanoResponse;
import br.com.central.api.comercial.dto.CatalogoDtos.PrecoDoPlanoRequest;
import br.com.central.api.comercial.dto.CatalogoDtos.PrecoResponse;
import br.com.central.api.comercial.dto.CatalogoDtos.PrecoVigenteResponse;
import br.com.central.api.comercial.dto.CatalogoDtos.ProdutoResponse;
import br.com.central.api.comercial.dto.CatalogoDtos.RecursoDoPlanoRequest;
import br.com.central.api.comercial.dto.CatalogoDtos.RecursoDoPlanoResponse;
import br.com.central.api.comercial.dto.CatalogoDtos.RecursoResponse;
import br.com.central.api.comercial.dto.CatalogoDtos.SalvarAdicionalRequest;
import br.com.central.api.comercial.dto.CatalogoDtos.SalvarPlanoRequest;
import br.com.central.api.comercial.dto.CatalogoDtos.SalvarProdutoRequest;
import br.com.central.api.comercial.dto.CatalogoDtos.SalvarRecursoRequest;
import br.com.central.api.web.BadRequestException;
import br.com.central.api.web.ConflictException;
import br.com.central.api.web.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class CatalogoService {

    private final ProdutoRepository produtoRepository;
    private final RecursoRepository recursoRepository;
    private final PlanoRepository planoRepository;
    private final PlanoRecursoRepository planoRecursoRepository;
    private final PrecoPlanoRepository precoPlanoRepository;
    private final AdicionalRepository adicionalRepository;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public CatalogoService(ProdutoRepository produtoRepository, RecursoRepository recursoRepository,
                           PlanoRepository planoRepository, PlanoRecursoRepository planoRecursoRepository,
                           PrecoPlanoRepository precoPlanoRepository, AdicionalRepository adicionalRepository,
                           Clock clock) {
        this.produtoRepository = produtoRepository;
        this.recursoRepository = recursoRepository;
        this.planoRepository = planoRepository;
        this.planoRecursoRepository = planoRecursoRepository;
        this.precoPlanoRepository = precoPlanoRepository;
        this.adicionalRepository = adicionalRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ProdutoResponse> listarProdutos() {
        return produtoRepository.findAllByOrderByNomeAsc().stream().map(CatalogoService::produto).toList();
    }

    @Transactional
    public ProdutoResponse criarProduto(SalvarProdutoRequest request) {
        String codigo = codigo(request.codigo());
        if (produtoRepository.existsByCodigo(codigo)) {
            throw new ConflictException("Já existe um produto com este código.", "CONFLITO");
        }
        Produto produto = new Produto(codigo, request.nome().trim());
        aplicar(produto, request);
        return produto(produtoRepository.saveAndFlush(produto));
    }

    @Transactional
    public ProdutoResponse atualizarProduto(UUID id, SalvarProdutoRequest request) {
        Produto produto = carregarProduto(id);
        produto.setNome(request.nome().trim());
        aplicar(produto, request);
        return produto(produto);
    }

    @Transactional(readOnly = true)
    public List<RecursoResponse> listarRecursos(UUID produtoId) {
        List<Recurso> recursos = produtoId == null
                ? recursoRepository.findAllByOrderByCodigoAsc()
                : recursoRepository.findByProduto_IdOrderByCodigoAsc(produtoId);
        return recursos.stream().map(CatalogoService::recurso).toList();
    }

    @Transactional
    public RecursoResponse criarRecurso(SalvarRecursoRequest request) {
        Produto produto = carregarProduto(request.produtoId());
        String codigo = codigoRecurso(request.codigo());
        if (recursoRepository.existsByProduto_IdAndCodigo(produto.getId(), codigo)) {
            throw new ConflictException("Já existe um recurso com este código neste produto.", "CONFLITO");
        }
        Recurso recurso = new Recurso(produto, codigo, request.nome().trim(), request.tipo());
        aplicar(recurso, request);
        return recurso(recursoRepository.saveAndFlush(recurso));
    }

    @Transactional
    public RecursoResponse atualizarRecurso(UUID id, SalvarRecursoRequest request) {
        Recurso recurso = carregarRecurso(id);
        recurso.setNome(request.nome().trim());
        recurso.setTipo(request.tipo());
        aplicar(recurso, request);
        return recurso(recurso);
    }

    /** Valor padrão só faz sentido em LIMITE: funcionalidade está ou não está no plano. */
    private static void aplicar(Recurso recurso, SalvarRecursoRequest request) {
        recurso.setUnidade(texto(request.unidade()));
        recurso.setValorPadrao(request.tipo() == TipoRecurso.LIMITE ? request.valorPadrao() : null);
    }

    @Transactional(readOnly = true)
    public List<PlanoResponse> listarPlanos(UUID produtoId) {
        List<Plano> planos = produtoId == null
                ? planoRepository.findAllByOrderByNomeAsc()
                : planoRepository.findByProduto_IdOrderByNomeAsc(produtoId);
        return planos.stream().map(this::plano).toList();
    }

    @Transactional
    public PlanoResponse criarPlano(SalvarPlanoRequest request) {
        Produto produto = carregarProduto(request.produtoId());
        String codigo = codigo(request.codigo());
        if (planoRepository.existsByProduto_IdAndCodigo(produto.getId(), codigo)) {
            throw new ConflictException("Já existe um plano com este código neste produto.", "CONFLITO");
        }
        Plano plano = new Plano(produto, codigo, request.nome().trim());
        if (request.ativo() != null) {
            plano.setAtivo(request.ativo());
        }
        plano = planoRepository.saveAndFlush(plano);
        substituirRecursos(plano, request.recursos());
        aplicarPrecos(plano, request.precos());
        return plano(plano);
    }

    @Transactional
    public PlanoResponse atualizarPlano(UUID id, SalvarPlanoRequest request) {
        Plano plano = carregarPlano(id);
        plano.setNome(request.nome().trim());
        if (request.ativo() != null) {
            plano.setAtivo(request.ativo());
        }
        substituirRecursos(plano, request.recursos());
        aplicarPrecos(plano, request.precos());
        return plano(plano);
    }

    /**
     * Preços informados no cadastro do plano (26/09/2026: antes era preciso
     * salvar o plano e depois clicar em "Novo preço"). Valor igual ao vigente
     * não gera histórico; diferente vale a partir de hoje, e dois ajustes no
     * mesmo dia corrigem a mesma linha. Contratação existente não muda: o
     * valor fica copiado nela.
     */
    private void aplicarPrecos(Plano plano, List<PrecoDoPlanoRequest> precos) {
        if (precos == null) {
            return;
        }
        LocalDate hoje = LocalDate.now(clock);
        Set<Periodicidade> vistas = new HashSet<>();
        for (PrecoDoPlanoRequest preco : precos) {
            if (!vistas.add(preco.periodicidade())) {
                throw new BadRequestException("Periodicidade repetida nos preços do plano.");
            }
            Optional<BigDecimal> vigente = precoVigente(plano.getId(), preco.periodicidade(), hoje);
            if (vigente.isPresent() && vigente.get().compareTo(preco.valor()) == 0) {
                continue;
            }
            precoPlanoRepository.findByPlanoIdAndPeriodicidadeAndVigenteDesde(plano.getId(), preco.periodicidade(), hoje)
                    .ifPresentOrElse(
                            doDia -> doDia.setValor(preco.valor()),
                            () -> precoPlanoRepository.save(new PrecoPlano(
                                    plano.getId(), preco.periodicidade(), preco.valor(), hoje)));
        }
        entityManager.flush();
    }

    @Transactional
    public PlanoResponse adicionarPreco(UUID planoId, NovoPrecoRequest request) {
        Plano plano = carregarPlano(planoId);
        if (precoPlanoRepository.existsByPlanoIdAndPeriodicidadeAndVigenteDesde(
                planoId, request.periodicidade(), request.vigenteDesde())) {
            throw new ConflictException("Já existe preço deste plano para esta periodicidade nesta data.", "CONFLITO");
        }
        precoPlanoRepository.saveAndFlush(new PrecoPlano(
                planoId, request.periodicidade(), request.valor(), request.vigenteDesde()));
        return plano(plano);
    }

    @Transactional(readOnly = true)
    public Optional<BigDecimal> precoVigente(UUID planoId, Periodicidade periodicidade, LocalDate data) {
        return precoPlanoRepository
                .findFirstByPlanoIdAndPeriodicidadeAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(
                        planoId, periodicidade, data)
                .map(PrecoPlano::getValor);
    }

    @Transactional(readOnly = true)
    public List<AdicionalResponse> listarAdicionais(UUID produtoId) {
        List<Adicional> adicionais = produtoId == null
                ? adicionalRepository.findAllByOrderByNomeAsc()
                : adicionalRepository.findByProduto_IdOrderByNomeAsc(produtoId);
        return adicionais.stream().map(CatalogoService::adicional).toList();
    }

    @Transactional
    public AdicionalResponse criarAdicional(SalvarAdicionalRequest request) {
        Produto produto = carregarProduto(request.produtoId());
        Recurso recurso = recursoDoProduto(request.recursoId(), produto.getId());
        String codigo = codigo(request.codigo());
        if (adicionalRepository.existsByProduto_IdAndCodigo(produto.getId(), codigo)) {
            throw new ConflictException("Já existe um adicional com este código neste produto.", "CONFLITO");
        }
        Adicional adicional = new Adicional(produto, recurso, codigo, request.nome().trim(),
                request.quantidade(), request.preco());
        if (request.ativo() != null) {
            adicional.setAtivo(request.ativo());
        }
        return adicional(adicionalRepository.saveAndFlush(adicional));
    }

    @Transactional
    public AdicionalResponse atualizarAdicional(UUID id, SalvarAdicionalRequest request) {
        Adicional adicional = carregarAdicional(id);
        adicional.setNome(request.nome().trim());
        adicional.setQuantidade(request.quantidade());
        adicional.setPreco(request.preco());
        if (request.ativo() != null) {
            adicional.setAtivo(request.ativo());
        }
        return adicional(adicional);
    }

    public Produto carregarProduto(UUID id) {
        return produtoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado."));
    }

    public Plano carregarPlano(UUID id) {
        return planoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plano não encontrado."));
    }

    public Adicional carregarAdicional(UUID id) {
        return adicionalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Adicional não encontrado."));
    }

    private void substituirRecursos(Plano plano, List<RecursoDoPlanoRequest> pedidos) {
        planoRecursoRepository.deleteByPlanoId(plano.getId());
        entityManager.flush();
        if (pedidos == null) {
            return;
        }
        Set<UUID> vistos = new HashSet<>();
        for (RecursoDoPlanoRequest pedido : pedidos) {
            if (!vistos.add(pedido.recursoId())) {
                throw new BadRequestException("Recurso repetido no plano.");
            }
            Recurso recurso = recursoDoProduto(pedido.recursoId(), plano.getProdutoId());
            planoRecursoRepository.save(new PlanoRecurso(plano.getId(), recurso, pedido.valor()));
        }
    }

    private Recurso recursoDoProduto(UUID recursoId, UUID produtoId) {
        Recurso recurso = carregarRecurso(recursoId);
        if (!recurso.getProdutoId().equals(produtoId)) {
            throw new BadRequestException("O recurso não pertence a este produto.");
        }
        return recurso;
    }

    private Recurso carregarRecurso(UUID id) {
        return recursoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
    }

    private PlanoResponse plano(Plano plano) {
        LocalDate hoje = LocalDate.now(clock);
        List<RecursoDoPlanoResponse> recursos = planoRecursoRepository.listarDoPlano(plano.getId()).stream()
                .map(item -> new RecursoDoPlanoResponse(
                        item.getRecurso().getId(), item.getRecurso().getCodigo(),
                        item.getRecurso().getTipo(), item.getValor()))
                .toList();
        List<PrecoResponse> precos = precoPlanoRepository.findByPlanoIdOrderByVigenteDesdeDesc(plano.getId()).stream()
                .map(preco -> new PrecoResponse(preco.getId(), preco.getPeriodicidade(), preco.getValor(),
                        preco.getVigenteDesde()))
                .toList();
        List<PrecoVigenteResponse> vigentes = new ArrayList<>();
        for (Periodicidade periodicidade : Periodicidade.values()) {
            precoVigente(plano.getId(), periodicidade, hoje)
                    .ifPresent(valor -> vigentes.add(new PrecoVigenteResponse(periodicidade, valor)));
        }
        return new PlanoResponse(
                plano.getId(), plano.getSequencial(), plano.getProdutoId(), plano.getCodigo(), plano.getNome(), plano.isAtivo(),
                vigentes, recursos, precos);
    }

    private static void aplicar(Produto produto, SalvarProdutoRequest request) {
        produto.setUrlBaseIntegracao(texto(request.urlBaseIntegracao()));
        if (request.ativo() != null) {
            produto.setAtivo(request.ativo());
        }
    }

    private static ProdutoResponse produto(Produto produto) {
        return new ProdutoResponse(produto.getId(), produto.getSequencial(), produto.getCodigo(), produto.getNome(),
                produto.getUrlBaseIntegracao(), produto.isAtivo());
    }

    private static RecursoResponse recurso(Recurso recurso) {
        return new RecursoResponse(recurso.getId(), recurso.getSequencial(), recurso.getProdutoId(), recurso.getCodigo(),
                recurso.getNome(), recurso.getTipo(), recurso.getUnidade(), recurso.getValorPadrao());
    }

    private static AdicionalResponse adicional(Adicional adicional) {
        return new AdicionalResponse(adicional.getId(), adicional.getSequencial(), adicional.getProdutoId(), adicional.getRecurso().getId(),
                adicional.getRecurso().getCodigo(), adicional.getCodigo(), adicional.getNome(),
                adicional.getQuantidade(), adicional.getPreco(), adicional.isAtivo());
    }

    static String codigo(String valor) {
        return valor.trim().toUpperCase();
    }

    /**
     * Código do recurso fica como o app usa (contrato 5.5): "voluntarios" e
     * "ESCALAS" convivem. Só tira espaços; em maiúsculas nunca casaria com o
     * que o app lê em {@code limites}.
     */
    static String codigoRecurso(String valor) {
        return valor.trim().replaceAll("\\s+", "_");
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
