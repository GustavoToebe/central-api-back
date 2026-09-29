# Cobranças

Cada período da contratação vira uma cobrança, com itens (plano e adicionais). O pagamento fica na própria cobrança.

Situações: **Aberta** (vira "Vencida" na tela quando passa do vencimento), **Paga**, **Isenta** e **Cancelada**.

- **Estornar** devolve uma cobrança paga para aberta.
- **Isentar** deixa uma cobrança aberta como isenta e não emite outra no lugar. Isenta não vence, não conta como recebido e continua ocupando a competência.
- **Cancelar e emitir nova** (`POST /contratacoes/{id}/cobrancas/{cobrancaId}/reemitir`) cancela a atual e cria outra em aberto na mesma competência. A cancelada não ocupa mais o período (V009).

## Isenção (V010)

- Cobrança de **valor zero** nasce isenta, com a observação "Valor zero". A migration passou para isentas as abertas de valor zero que já existiam.
- A **contratação** pode ficar isenta (`POST /contratacoes/{id}/isencao` com `motivo` e, opcional, `ate` em `AAAA-MM`). As cobranças das competências até `ate` (sem `ate`, sem fim) nascem isentas, e as abertas desse período ficam isentas na hora.
- **Encerrar** (`DELETE /contratacoes/{id}/isencao`): as próximas nascem normais, e as isentas de valor maior que zero geradas para depois deste mês voltam a abertas.
- O job diário tira de `INADIMPLENTE` quem ficou sem cobrança vencida em aberto (histórico `REGULARIZADA`).

A lista mostra só o mês de referência ("09/2026"). Vencimento e pagamento têm colunas próprias; o intervalo de doze meses do plano anual fica no detalhe, no campo de período.
