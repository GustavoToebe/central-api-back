# Cobranças

Cada período da contratação vira uma cobrança, com itens (plano e adicionais). O pagamento fica na própria cobrança.

- **Estornar** devolve uma cobrança paga para aberta.
- **Isentar** cancela uma cobrança aberta e não emite outra no lugar.
- **Cancelar e emitir nova** (`POST /contratacoes/{id}/cobrancas/{cobrancaId}/reemitir`) cancela a atual e cria outra em aberto na mesma competência. A cancelada não ocupa mais o período (V009).

A lista mostra o mês de referência e o vencimento. O intervalo de doze meses do plano anual fica no detalhe, no campo de período.
