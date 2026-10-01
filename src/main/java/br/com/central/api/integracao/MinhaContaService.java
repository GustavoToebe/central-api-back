package br.com.central.api.integracao;

import br.com.central.api.comercial.CobrancaRepository;
import br.com.central.api.comercial.Contratacao;
import br.com.central.api.comercial.ContratacaoRepository;
import br.com.central.api.comercial.ContratacaoAdicionalRepository;
import br.com.central.api.comercial.Produto;
import br.com.central.api.comercial.ProdutoRepository;
import br.com.central.api.web.ResourceNotFoundException;
import br.com.central.api.comercial.ComercialConfiguration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class MinhaContaService {

    private final ContratacaoRepository contratacaoRepository;
    private final CobrancaRepository cobrancaRepository;
    private final ProdutoRepository produtoRepository;
    private final ContratacaoAdicionalRepository contratacaoAdicionalRepository;

    public MinhaContaService(ContratacaoRepository contratacaoRepository,
                             CobrancaRepository cobrancaRepository,
                             ProdutoRepository produtoRepository,
                             ContratacaoAdicionalRepository contratacaoAdicionalRepository) {
        this.contratacaoRepository = contratacaoRepository;
        this.cobrancaRepository = cobrancaRepository;
        this.produtoRepository = produtoRepository;
        this.contratacaoAdicionalRepository = contratacaoAdicionalRepository;
    }

    @Transactional(readOnly = true)
    public MinhaContaDto obterMinhaConta(String produtoCodigo, UUID idExterno) {
        Produto p = produtoRepository.findByCodigoIgnoreCase(produtoCodigo)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        Contratacao contratacao = contratacaoRepository.findByProduto_IdAndIdExterno(p.getId(), idExterno)
                .orElseThrow(() -> new ResourceNotFoundException("Instância não encontrada"));

        MinhaContaDto.ClienteDto clienteDto = new MinhaContaDto.ClienteDto(
                contratacao.getCliente().getNome(),
                contratacao.getCliente().getDocumento(),
                new MinhaContaDto.EnderecoDto(
                        contratacao.getCliente().getLogradouro(),
                        contratacao.getCliente().getNumero(),
                        contratacao.getCliente().getComplemento(),
                        contratacao.getCliente().getBairro(),
                        contratacao.getCliente().getCidade(),
                        contratacao.getCliente().getUf(),
                        contratacao.getCliente().getCep()
                ),
                contratacao.getCliente().getContatos().stream().map(c -> new MinhaContaDto.ContatoDto(
                        c.getNome(), c.getEmail(), c.getTelefone(), c.isPrincipal()
                )).toList()
        );

        MinhaContaDto.ContratacaoDto contratacaoDto = new MinhaContaDto.ContratacaoDto(
                contratacao.getPlano().getNome(),
                contratacao.getPeriodicidade(),
                contratacao.getValor(),
                contratacao.getDiaVencimento(),
                contratacao.getInicio(),
                contratacao.getVigenteAte(),
                contratacao.getSituacaoComercial(),
                contratacao.getNomeInstancia(),
                contratacaoAdicionalRepository.listarDaContratacao(contratacao.getId()).stream().map(a -> new MinhaContaDto.AdicionalDto(
                        a.getAdicional().getNome(),
                        a.getQuantidade()
                )).toList()
        );

        LocalDate hoje = LocalDate.now(ComercialConfiguration.FUSO);
        List<MinhaContaDto.CobrancaDto> cobrancasDto = cobrancaRepository.findByContratacaoIdOrderByVencimentoDesc(contratacao.getId())
                .stream().map(c -> new MinhaContaDto.CobrancaDto(
                        c.getId(),
                        c.getCompetenciaInicio(),
                        c.getCompetenciaFim(),
                        c.getVencimento(),
                        c.getValor(),
                        c.getStatus().name(),
                        c.getVencimento() != null && c.getVencimento().isBefore(hoje) && c.getStatus() == br.com.central.api.comercial.Cobranca.Status.ABERTA,
                        c.getPagoEm()
                )).toList();

        return new MinhaContaDto(clienteDto, contratacaoDto, cobrancasDto);
    }
}
