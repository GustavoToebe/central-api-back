# Financeiro operacional da Central

Implementação local: V014. Uso exclusivo do operador (`ROLE_OPERADOR`), sem acesso por HMAC de aplicativos. Não contém dados de pessoas das paróquias.

## Operação simples

1. Cadastre contas/bancos e monte o **Plano de contas** (V017, antes "Categorias"): **grupos** (ex.: Despesas fixas, Receitas de serviços) e, dentro deles, **contas contábeis** (Infraestrutura, Comunicação, Consultoria). O grupo organiza e define o tipo (entrada ou saída); só a conta contábil recebe lançamento e ela herda o tipo do grupo.
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

## Plano de contas

`financeiro_categoria` guarda os dois níveis: sem `grupo_id` é grupo, com `grupo_id` é conta contábil (só dois níveis). Regras: a conta precisa do mesmo tipo do grupo; grupo com contas não vira conta, conta com lançamentos não vira grupo; o tipo não muda enquanto houver contas ou lançamentos; lançamento recusa grupo, tipo diferente e conta ou grupo inativos; nome único entre os grupos de um tipo e entre as contas de um grupo. A V017 converteu as categorias antigas em contas contábeis dentro de "Saídas (migradas)" ou "Entradas (migradas)", com o tipo vindo dos lançamentos (saída quando misto ou nunca usada). Não há rateio nem centro de custo.

## Relatórios

Tela **Relatórios** do painel, só para o operador, sempre de leitura. Todos aceitam período (`de` e `ate`) e recusam mais de 5.000 lançamentos (reduza o período em vez de receber um relatório cortado).

- **Relatório de despesas** e **de receitas** (`/financeiro/relatorios/despesas|receitas`): agrupados por grupo e conta contábil, com total e os lançamentos de cada conta. `visao=REALIZADO` (padrão) usa a data da baixa; `visao=PREVISTO` mostra os pendentes pelo vencimento. As receitas incluem o grupo calculado "Assinaturas dos aplicativos" com as cobranças pagas (ou em aberto, na visão prevista); ele não faz parte do plano de contas.
- **Relatório de banco/caixa** (`/financeiro/relatorios/banco-caixa`, `contaId` opcional): por conta/banco, saldo anterior, entradas, saídas, saldo corrido e saldo final, só com baixas. Saldo inicial que cai dentro do período aparece como a primeira linha. O saldo final bate com o `saldoTotal` do resumo na mesma data. Cobranças de assinatura não têm vínculo bancário e ficam de fora.
- **Demonstrativo do resultado do exercício** (`/financeiro/relatorios/demonstrativo`): receitas e despesas realizadas por grupo e conta, resultado, a receber, a pagar, resultado previsto e saldos das contas ao fim do período.

Lançamento antigo em conta ou grupo de outro tipo (dado de antes do plano de contas) aparece com o aviso "(conta cadastrada como saída/entrada)" em vez de ser misturado em silêncio. A tela imprime (diálogo do navegador) e baixa CSV. Valores em reais, calendário de São Paulo.

## API

GET/POST `/financeiro/contas`, PUT `/financeiro/contas/{id}`; categorias seguem o mesmo padrão. GET `/financeiro/movimentos` exige de/ate (vencimento), aceita nome, contaId, categoriaId, situacao, tipo, pagina e tamanho (1–100). POST/PUT criam/editam pendentes. POST `/financeiro/movimentos/{id}/baixar` recebe dataPagamento/versao; `/estornar` e `/cancelar` recebem versao. GET `/financeiro/resumo?de=...&ate=...` devolve realizados, previstos e saldos em snapshot REPEATABLE_READ.

V014 cria financeiro_conta, financeiro_categoria e financeiro_movimento com RLS sem políticas e revogação anon/authenticated. Testes com PostgreSQL verificam projeção, baixas, estorno, conflitos e acesso; billing verifica inclusão de pagamento comercial sem replicação em contas.
