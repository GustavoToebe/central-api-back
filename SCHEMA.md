# Banco da Central

Postgres do painel. O Servire tem o banco da paróquia (`SCHEMA.md` no `servirea-api-back`). Este arquivo lista o que a Central usa. Atualizar quando entrar migration nova.

Flyway: `src/main/resources/db/migration`, V001–V012. Migration já aplicada não se edita.

Conferido com o schema exportado em 30/09/2026. Cada tabela abaixo tem entidade no código.

## Quem entra no painel

`operador` (login), `refresh_token` (sessão), `operador_log` (trilha).

## Catálogo

`produto` → `recurso` e `plano`. O que o plano inclui está em `plano_recurso`. Preço por periodicidade em `preco_plano`. Extra pago em `adicional`.

## Cliente e contrato

`cliente` e `cliente_contato`.

`contratacao` liga cliente, produto e plano. `id_externo` é o id da paróquia no Servire. Situação comercial: `TRIAL`, `ATIVA`, `INADIMPLENTE`, `BLOQUEADA`, `CANCELADA`. Provisionamento: `PENDENTE`, `PROCESSANDO`, `ATIVA`, `ERRO`. Direitos atuais em `direitos_atuais` (JSON). Isenção: `isenta`, `isencao_motivo`, `isenta_ate`.

`contratacao_adicional` e `historico_contratacao`.

## Cobrança e integração

`cobranca` (status `ABERTA`, `PAGA`, `CANCELADA`, `ISENTA`) e `cobranca_item` (`PLANO` ou `ADICIONAL`).

`evento_saida` é a fila que avisa o aplicativo. `erro_aplicativo` guarda a falha que o aplicativo reporta. `integracao_nonce` evita repetir a mesma chamada.

`flyway_schema_history` é o controle do Flyway.

## Esquema completo
`schema.sql` (nesta pasta) é o esquema inteiro, com colunas, chaves, índices e checks, gerado das migrations V001–V012 num Postgres limpo. **Migration nova: rodar `scripts/gerar-schema.ps1` e commitar o `schema.sql` junto.** Nunca editar o arquivo à mão.
V011: `evento_saida.reservado_por` e `reserva_ate` registram posse e expiração do envio. Uma conclusão só altera o item se continuar dona da reserva.

V012: checkout_mercadopago congela vínculo/valor da tentativa e guarda link hospedado. pagamento_mercadopago é caixa durável com revisão, reserva temporária e resultado da conciliação; mantém confirmação aplicada mesmo após refund/chargeback para revisão. Ambas com RLS, sem acesso anon/authenticated.
