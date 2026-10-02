# Documentos da Central

- [Fontes e retomada](desenvolvimento/fontes-e-retomada.md): precedência, estado atual e histórico.

Índice. `AGENTS.md` fica na raiz. O texto longo continua em dois arquivos, e as pastas abaixo são o mapa por assunto.

| Pasta ou arquivo | Assunto |
| --- | --- |
| [clientes](clientes/README.md) | Quem contrata |
| [contratacoes](contratacoes/README.md) | Instância, plano, situação e provisionamento |
| [cobrancas](cobrancas/README.md) | Competência, pagamento, estorno e nova emissão |
| [integracao](integracao/README.md) | HMAC, direitos e erros dos aplicativos |
| [arquitetura-central.md](arquitetura-central.md) | Desenho completo e decisões numeradas |
| [contrato-integracao-v1.md](contrato-integracao-v1.md) | Contrato com os aplicativos |
| [../AGENTS.md](../AGENTS.md) | Regras de implementação |
| [../README.md](../README.md) | Como rodar |
| [../deploy/README.md](../deploy/README.md) | Produção na VPS |

- [Limites de integração](integracao-limites.md): payload, nonce e erros do filtro HMAC.

- [outbox.md](outbox.md): reserva, transações curtas, concorrência e retomada após crash.

- [Mercado Pago](mercadopago.md): configuração, checkout, notificações e limites da conciliação.

## Entrada rápida

- [estado-projeto.json](estado-projeto.json): componente, comandos e migration local quando houver. Não confirma publicação.
- Histórico explica decisões antigas; contrato e código atuais definem o comportamento vigente.

- [Limites de login](login-limites.md): tentativas, saturação e limites por instância.

- [MFA dos operadores](mfa-operadores.md): segundo fator, recuperação, cifra e encerramento de sessões.

- [Financeiro operacional](financeiro-operacional.md): comportamento, contratos e limitações.

## Quatro entregas de produto — 01/10/2026

- [Consumo de instâncias](consumo-instancias.md).

## Rodada 6–10 — 02/10/2026

- [historico consumo ](historico-consumo.md): contrato, limites e homologação.

## Rodada 11–17 — 02/10/2026

- [monitoramento ](monitoramento.md).
- [concorrencia-comercial ](concorrencia-comercial.md).

- [Ambiente local atual](ambiente-local-atual.md): bases novas e portas 8070/8071/4210/4211.

- [Painel de instâncias](painel-instancias.md): F06/F26, último resumo guardado, nível por gravidade, atualização manual limitada e métricas para alertas externos.
