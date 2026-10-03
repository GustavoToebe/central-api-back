package br.com.central.api.financeiro;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

import static br.com.central.api.financeiro.MovimentoFinanceiro.Tipo;
import static br.com.central.api.financeiro.RelatorioFinanceiroDtos.*;

/** Relatórios do financeiro do operador (só leitura). O front monta a tela, a impressão e o CSV a partir destes dados. */
@RestController
@RequestMapping("/financeiro/relatorios")
public class RelatorioFinanceiroController {
    private final RelatorioFinanceiroService servico;

    public RelatorioFinanceiroController(RelatorioFinanceiroService servico) { this.servico = servico; }

    @GetMapping("/despesas") @PreAuthorize("hasRole('OPERADOR')")
    public PorTipo despesas(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                            @RequestParam(defaultValue = "REALIZADO") Visao visao) {
        return servico.porTipo(Tipo.DESPESA, visao, de, ate);
    }

    @GetMapping("/receitas") @PreAuthorize("hasRole('OPERADOR')")
    public PorTipo receitas(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                            @RequestParam(defaultValue = "REALIZADO") Visao visao) {
        return servico.porTipo(Tipo.RECEITA, visao, de, ate);
    }

    @GetMapping("/banco-caixa") @PreAuthorize("hasRole('OPERADOR')")
    public BancoCaixa bancoCaixa(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                                 @RequestParam(required = false) UUID contaId) {
        return servico.bancoCaixa(de, ate, contaId);
    }

    @GetMapping("/demonstrativo") @PreAuthorize("hasRole('OPERADOR')")
    public Demonstrativo demonstrativo(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return servico.demonstrativo(de, ate);
    }
}
