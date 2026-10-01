# Financeiro operacional da Central

Implementação local: V014. Uso exclusivo do operador (`ROLE_OPERADOR`), sem acesso por HMAC de aplicativos. Não contém dados de pessoas das paróquias.

## Operação simples

1. Cadastre contas/bancos e categorias como Infraestrutura, Comunicação e Serviços.
2. Registre VPS, Supabase, Resend e outras despesas, com valor em reais e vencimento. Lançamentos começam PENDENTES, inclusive provisões futuras.
3. Dê baixa somente após pagamento/recebimento efetivo. Estornar volta para pendente; cancelar só é permitido antes da baixa.
4. Assinaturas pagas entram automaticamente a partir de `cobranca.valor_pago`, pela data do pagamento. Não cadastrar essas receitas novamente como receitas manuais. Cobranças abertas entram no previsto pelo vencimento; canceladas/isentas ficam fora.

Todos os valores são BRL: serviço em dólar deve ser registrado pela estimativa em reais ou pelo valor efetivamente pago. Não há conversão cambial automática, importação bancária, recorrência de despesas nem contabilidade fiscal.

## Cálculos e limites

- Resultado realizado = cobranças comerciais pagas + outras receitas baixadas − despesas baixadas, no período pela data da baixa.
- A receber = outras receitas pendentes + cobranças abertas já geradas, com vencimento no período. Não extrapola mensalidades ainda não geradas nem inclui vencidos fora do período.
- A pagar = despesas pendentes com vencimento no período.
- Resultado previsto = resultado realizado + a receber − a pagar. Não representa saldo disponível ou garantia de recebimento.
- Saldo por conta = saldo inicial + receitas manuais baixadas − despesas baixadas até a data final. Cobranças comerciais não entram nesse saldo porque ainda não há vínculo bancário; são mostradas separadamente no resumo. Contas inativas conservam histórico.

Valores têm duas casas; lançamentos positivos. Datas de baixa ficam entre saldo inicial e hoje em America/Sao_Paulo. O saldo inicial não muda depois do primeiro lançamento. Atualização/baixa/estorno/cancelamento exigem a versão do lançamento; concorrência usa trava e devolve 409 CONFLITO_FINANCEIRO para versão antiga. Trilha registra identificador/ação, sem valores ou descrição no log, na mesma transação do lançamento; rollback também desfaz a trilha. A auditoria de recusas de login continua com sua transação própria.

## API

GET/POST `/financeiro/contas`, PUT `/financeiro/contas/{id}`; categorias seguem o mesmo padrão. GET `/financeiro/movimentos` exige de/ate (vencimento), aceita nome, contaId, categoriaId, situacao, tipo, pagina e tamanho (1–100). POST/PUT criam/editam pendentes. POST `/financeiro/movimentos/{id}/baixar` recebe dataPagamento/versao; `/estornar` e `/cancelar` recebem versao. GET `/financeiro/resumo?de=...&ate=...` devolve realizados, previstos e saldos em snapshot REPEATABLE_READ.

V014 cria financeiro_conta, financeiro_categoria e financeiro_movimento com RLS sem políticas e revogação anon/authenticated. Testes com PostgreSQL verificam projeção, baixas, estorno, conflitos e acesso; billing verifica inclusão de pagamento comercial sem replicação em contas.
