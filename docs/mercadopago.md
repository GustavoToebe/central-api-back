# Mercado Pago — checkout por cobrança

## Fluxo implementado

Operador abre detalhe de cobrança positiva em aberto e gera link hospedado. API lê valor e contratação do banco, congela tentativa por Idempotency-Key UUID e retorna link após HTTP fora da transação. Repetir tentativa pronta reutiliza o link; mudar valor exige nova tentativa. Não armazena cartão.

POST /cobrancas/{id}/checkout exige ROLE_OPERADOR. GET /cobrancas/{id}/pagamentos-online mostra conciliação; POST /cobrancas/pagamentos-online/{id}/reconciliar solicita nova consulta. A tentativa é persistida, mas criar várias tentativas para a mesma cobrança ainda pode produzir links distintos: pagamentos adicionais são sinalizados para revisão.

POST /webhooks/mercadopago é público somente para POST e valida HMAC-SHA256 de x-signature, x-request-id e query data.id. Configurar tópico payment. Body não é utilizado para decidir pagamento; assinatura segue [documentação oficial](https://www.mercadopago.com.br/developers/pt/docs/checkout-pro-preferences/payment-notifications). Persistência da notificação ocorre antes de responder 200. Repetição incrementa revisão; não duplica baixa. Não há rejeição por idade do timestamp, permitindo reentrega: segurança depende da assinatura, consulta oficial e idempotência da baixa, não de tratar notificações como ordens de pagamento.

Conciliador reserva um pagamento por vez com lease de 120 segundos e SKIP LOCKED. GET /v1/payments/{id} ocorre fora da transação. Para approved, exige referência da tentativa conhecida, valor igual ao checkout e cobrança atual, BRL, collector-id e live_mode correspondentes ao ambiente. Pagamentos pendentes são consultados novamente em 15 minutos. Falhas técnicas tentam novamente em cinco minutos; após 12 falhas ficam REVISAR. Evento novo reativa processamento.

Reserva confere posse e revisão antes de concluir. Baixa usa BillingService na mesma transação do registro aplicado e mantém direitos/outbox da regra existente. Bloqueio manual da contratação não é removido pelo pagamento. Valor divergente, cobrança cancelada/paga por outra via, reembolso ou contestação após baixa ficam REVISAR: nenhum estorno automático apaga o histórico. Detalhe apresenta pendências associadas; casos de referência desconhecida precisam de acompanhamento operacional pelo banco até painel geral futuro.

## Configuração

- MP_ACCESS_TOKEN: segredo da aplicação, somente no servidor.
- MP_WEBHOOK_SECRET: assinatura secreta das notificações da aplicação.
- MP_COLLECTOR_ID: identificador da conta recebedora; obrigatório para conciliar.
- MP_RETORNO_URL: HTTPS da tela de cobranças ou retorno próprio. Retorno do navegador não confirma pagamento.
- MP_NOTIFICACAO_URL: HTTPS pública da API terminando em /webhooks/mercadopago. Cadastrar webhook no painel Mercado Pago com tópico payment.
- MP_PRODUCAO=false para contas de teste; true somente com credenciais e conta de produção correspondentes.

Sem configuração, checkout informa indisponibilidade e job não chama o provedor. HTTP tem connect timeout 5s e read timeout 20s. Nenhuma cobrança real foi criada na implementação ou nos testes.

Esta etapa usa [Checkout Pro Preferences](https://www.mercadopago.com.br/developers/pt/reference/online-payments/checkout-pro-preferences/overview) sobre as cobranças por competência da Central. **Não implementa débito recorrente automático/preapproval**, Pix Automático, portal de autoassinatura nem ajuste/pró-rata automático. Esses fluxos permanecem no programa de evolução; não apresentar este link como autorização de cobrança recorrente.

Antes da publicação, testar com contas de teste distintas: aprovação, pendência, duplicata, alteração de valor, estorno/chargeback, queda após envio e notificação durante processamento. Depois verificar retorno HTTPS, domínio de checkout, tópico/assinatura e conciliação na conta escolhida. Testes locais simulam API oficial; homologação externa ainda pendente.
