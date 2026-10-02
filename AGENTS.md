# AGENTS.md — central-api-back

Backend Java da **Central**: gestão comercial (clientes, contratações, planos,
cobranças, direitos) de todos os aplicativos do ecossistema. O front é o
irmão **`central-api-front`** (outro git). O primeiro aplicativo atendido é o
Servirea (`servire-api-back` / `servire-api-front`). Idioma do código,
comentários, mensagens de erro e commits: **português**.

- Índice: `docs/README.md`. Desenho e decisões: `docs/arquitetura-central.md`.
- Contrato com os apps: `docs/contrato-integracao-v1.md`. Mudança de contrato
  atualiza o documento **junto** com o código, nos dois lados.

## Manter este arquivo atualizado
Arquivo de instruções compartilhado entre ferramentas de IA; o `CLAUDE.md`
só importa este (`@AGENTS.md`). Edite **só aqui**. Mudança que invalida algo
daqui atualiza este arquivo junto com a funcionalidade. Manter curto.

Nesta tarefa, commits usam `melhoria/ecossistema-sem-ia`, conforme solicitação do usuário.

## Regras que não podem ser quebradas
- A Central **não é multi-tenant**: sem `@TenantId`/`TenantContext`. Só os
  operadores do SaaS a usam.
- A Central **nunca** guarda senha de usuário de aplicativo nem gera token de
  aplicativo. Sessão de suporte = pedir um código de uso único ao app.
- A Central não recebe dado de negócio dos apps (nomes de pessoas etc.), só contagens e erros de servidor
  (5xx, contrato 6.2) com o usuário só pelo id.
- Toda alteração de contratação grava a nova versão dos direitos e o evento de
  saída (outbox) **na mesma transação**.
- Provisionamento sempre com `Idempotency-Key` = id da contratação, igual em
  toda repetição; horário, nonce e assinatura novos a cada envio.
- Segredos só em env var, sem valor padrão; nunca no git.

## Stack
Java 21 · Spring Boot 4.1.1 · Hibernate 7 · PostgreSQL 17 (Supabase, 17.6 em produção) · Flyway ·
Testcontainers · Maven. Mesmas convenções do `servire-api-back` (DTOs `record`,
`ApiException`, `@Transactional` no service, javadoc explicando o porquê).
A Central **não** é multi-tenant: sem `@TenantId` e sem `TenantContext`.

## Onde está o código
- `web/` — `ApiError` (com `codigo`), `ApiException`, `GlobalExceptionHandler`, `RequestIdFilter`,
  `Formatos` (CPF/CNPJ do cliente conforme PF/PJ, CNPJ alfanumérico, CEP, UF, telefone; e-mail com
  `@Email(regexp = Formatos.EMAIL)`). Grava sempre formatado; mesmas regras do Servirea e do front.
  Teste que cria cliente usa `Documentos.cpf()`/`cnpj()` (documento válido e único).
- `security/` — JWT próprio (`CENTRAL_JWT_SEGREDO`), BCrypt, cookie httpOnly de refresh.
- `operador/` — login, refresh, logout, `GET /operadores/eu`, `PUT /operadores/eu/senha` (confere a atual, derruba
  as outras sessões e devolve cookie de refresh novo). Seed só no profile `dev`, se `CENTRAL_OPERADOR_SEED_EMAIL` e
  `CENTRAL_OPERADOR_SEED_SENHA` existirem; em produção o primeiro operador entra pelo `psql` (`deploy/README.md`).
- `integracao/` — `HmacAssinatura`, filtro de `/integracao/**` (não é `permitAll`),
  nonce com `INSERT ... ON CONFLICT`, cliente de saída (`AplicativoHttp`) e o job
  que entrega o `evento_saida`. `GET /integracao/v1/produtos/{produto}/direitos`
  lista só contratações já provisionadas. `GET /produtos/{id}/recursos-do-app` pergunta ao app os códigos
  de limite/funcionalidade (contrato 5.5); código de recurso fica como o app usa (sem maiúsculas).
  `POST /integracao/v1/produtos/{produto}/erros` recebe erros 5xx (`erro_aplicativo`, V006, 90 dias; `GET /erros`
  para a tela "Logs"). `GET /integracao/v1/produtos/{produto}/instancias/{idExterno}/minha-conta` (contrato 6.3,
  `MinhaContaService`): só o que a paróquia pode ver, cobranças só da contratação. `POST /contratacoes/{id}/suporte` e
  `POST /contratacoes/{id}/tentar-provisionamento` são do operador.
- `comercial/` — cliente, produto, recurso, plano, preço, adicional, contratação,
  direitos, cobrança manual e `evento_saida`. O job diário gera cobranças e marca
  `INADIMPLENTE`; não bloqueia. Cobrança tem itens (`cobranca_item`: plano + adicionais,
  preço mensal × meses); valor = soma dos itens; adicionais mudaram → refaz só as abertas
  não vencidas. Competência começa no dia 1. `/cobrancas` = lista geral, detalhe e pagamento em lote.
  Cobrança `ISENTA` (V010): valor zero nasce isenta; contratação isenta (`isenta`, `isencao_motivo`, `isenta_ate`)
  isenta as competências do período; ver `docs/cobrancas/README.md`.
  Número curto para o operador: coluna `sequencial` (identity, V007) em cliente, contratação, cobrança,
  produto, plano, adicional e recurso; `@Generated` na entidade. Não é `numero` (cliente já tem, do endereço). Provisionar espera 1 min, 5 min, 15 min e 1 h e
  então fica `ERRO`; cancelada antes de chegar ao app não é provisionada (evento `DESCARTADO`). O webhook de direitos espera até 72 h e então fica `FALHOU`.
- Fora de `/auth/**` e `/integracao/**`, a rota exige operador autenticado. Exceção operacional: `GET /monitoramento/metrics` aceita apenas a credencial exclusiva de coleta, sem acesso comercial.
- Profile `dev` importa `application-dev-local.yml` (gitignorado). Segredos sem valor padrão.
- CSRF: domínio do `CENTRAL-XSRF-TOKEN` (nome próprio: o Servirea usa `XSRF-TOKEN` no mesmo domínio pai) em `CENTRAL_CSRF_COOKIE_DOMAIN` (painel e API em subdomínios =
  domínio pai; obrigatória em produção). Variáveis de produção sem valor padrão, conferidas por
  `ConfiguracaoProducaoTest`.
- Idempotency-Key é sempre o id da contratação. Nome, slug e admin só mudam antes de um envio que
  possa ter criado a instância (`Contratacao.dadosDeProvisionamentoEditaveis`, V003); senão 409
  `PROVISIONAMENTO_NAO_EDITAVEL`.
- Produção: `deploy/` (compose com as duas APIs e o Caddy, `atualizar.sh`, roteiro no README de lá).
- Testes: `mvn clean verify` (Docker, Postgres 17 — mesma versão major da produção). Ao somar migration, atualizar o total
  em `FlywayMigrationIntegrationTest`. **Migration nova também pede `scripts/gerar-schema.ps1` e o `schema.sql` commitado junto** (mapa do banco em `SCHEMA.md` + `schema.sql`).

## Segurança e CI na branch de melhorias

- `/integracao/**`: corpo de entrada até 1 MiB; headers baratos são conferidos antes da leitura, inclusive sem Content-Length. Contrato em `docs/integracao-limites.md`.
- Pull requests executam CI; o job de publicação aceita apenas main e nunca publica um PR. Backends usam `mvn verify`.

- Deploy versionado: healthchecks das duas APIs e espera de saúde no Compose; corpo no Caddy até 6 MiB. Ver `deploy/README.md`. Ainda não substitui rollback nem ensaio de restauração.

## Outbox com reserva (V011)
- Reserva e conclusão em transações curtas; HTTP fora delas. `docs/outbox.md` explica posse, expiração e garantia pelo menos uma vez.
- Ordem de lock: contratação antes do evento. Ler contratacaoId por projeção antes da trava para não carregar evento obsoleto no persistence context.
- Nunca limpar reserva ao descartar uma versão ainda em envio. Conclusão confere UUID de posse e prazo; resultado antigo não altera nova tentativa.
- Se os direitos mudarem durante POST, preservar a versão atual e a identidade criada. O PUT seguinte precisa preencher tenantId da instância.

## Checkout Mercado Pago (V012)

- docs/mercadopago.md: checkout por cobrança, sem débito recorrente automático. Valor vem do servidor.
- Webhook POST /webhooks/mercadopago valida assinatura e persiste caixa antes de 200. Nunca confiar no body/retorno do navegador para dar baixa.
- Conciliador HTTP fora de transação, fonte oficial, valor/moeda/coletor/ambiente, posse e revisão. Refund/chargeback exige revisão; nunca apaga baixa automaticamente.

## Fontes e estado verificável

- MFA opt-in de operador (V013): `docs/mfa-operadores.md`. Segredos cifrados por keyring próprio, recuperação em hash. Login/alteração/refresh serializam operador com FOR NO KEY UPDATE; respeitar esta ordem e reler refresh após trava. Ativar/desativar incrementa versão JWT e apaga sessões. Nunca logar chave TOTP/códigos/senhas.

- [Estado local](docs/estado-projeto.json) e [índice](docs/README.md). Histórico/plano não define a versão implantada.
- Executar `python scripts/verificar-docs.py` ao mudar docs, schema ou migrations; atualizar o estado junto.
- Nesta tarefa, usar `melhoria/ecossistema-sem-ia`, conforme pedido do usuário. Publicação depende do fluxo e autorização vigentes.

- Financeiro do operador (V014): docs/financeiro-operacional.md. Não copiar cobranças como receitas manuais; saldo por conta exclui cobrança sem vínculo bancário.

- Nova rodada de produto: docs/consumo-instancias.md: consulta de contratação sob demanda, HTTP fora da transação, sem dados pessoais/zero fictício.

- [Fontes e retomada](docs/desenvolvimento/fontes-e-retomada.md): precedência, histórico e registro de evidências.

- Rodada 6–10: Histórico de consumo sob demanda: docs/historico-consumo.md; não gerar zeros para consultas ausentes nem gravar dados pessoais.

- Rodada 11–17: docs/concorrencia-comercial.md: job por contratação, raiz antes de cobranças.
- Métricas: docs/monitoramento.md; credencial exclusiva e opcional, sem dados pessoais ou acesso de negócio. Endpoint bloqueado no proxy público; não ativar operação externa como efeito da implementação.

- F06/F26/T14: docs/painel-instancias.md. /instancias lê só resumos guardados (SEM_DADOS nunca é zero); atualização manual em série, máx. 5 por chamada, HTTP fora de transação; métricas agregadas por nível para alertas externos.

- T16: docs/papeis-banco.md. MIGRATION_DB_* separa as credenciais do Flyway; app só dados com BYPASSRLS; scripts/roles-banco.sql e adotar-papeis-banco.sql, ensaiar em staging.
- T17: docs/contrato-api.md. Mudou rota, permissão ou DTO: atualizar docs/contrato-api.json com `mvn test -Dtest=ContratoApiTest -Dcontrato.atualizar=true`; rota nova exige @PreAuthorize.
- T01: docs/release-rollback.md. atualizar.sh etiqueta imagens pelo commit e grava releases/*.json; rollback.sh recusa voltar se a migration avançou; testar com deploy/testes/release.test.sh.
