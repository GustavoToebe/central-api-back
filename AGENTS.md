# AGENTS.md — central-api-back

Backend Java da **Central**: gestão comercial (clientes, contratações, planos,
cobranças, direitos) de todos os aplicativos do ecossistema. O front é o
irmão **`central-api-front`** (outro git). O primeiro aplicativo atendido é o
Servire (`servire-api-back` / `servire-api-front`). Idioma do código,
comentários, mensagens de erro e commits: **português**.

- Desenho e decisões: `docs/arquitetura-central.md`.
- Contrato com os apps: `docs/contrato-integracao-v1.md`. Mudança de contrato
  atualiza o documento **junto** com o código, nos dois lados.

## Manter este arquivo atualizado
Arquivo de instruções compartilhado entre ferramentas de IA; o `CLAUDE.md`
só importa este (`@AGENTS.md`). Edite **só aqui**. Mudança que invalida algo
daqui atualiza este arquivo junto com a funcionalidade. Manter curto.

Commits vão direto na `main` (decisão de 25/09/2026).

## Regras que não podem ser quebradas
- A Central **não é multi-tenant**: sem `@TenantId`/`TenantContext`. Só os
  operadores do SaaS a usam.
- A Central **nunca** guarda senha de usuário de aplicativo nem gera token de
  aplicativo. Sessão de suporte = pedir um código de uso único ao app.
- A Central não recebe dado de negócio dos apps (nomes de pessoas etc.), só contagens.
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
- `web/` — `ApiError` (com `codigo`), `ApiException`, `GlobalExceptionHandler`, `RequestIdFilter`.
- `security/` — JWT próprio (`CENTRAL_JWT_SEGREDO`), BCrypt, cookie httpOnly de refresh.
- `operador/` — login, refresh, logout, `GET /operadores/eu`. Seed só no profile `dev`,
  se `CENTRAL_OPERADOR_SEED_EMAIL` e `CENTRAL_OPERADOR_SEED_SENHA` existirem.
- `integracao/` — `HmacAssinatura`, filtro de `/integracao/**` (não é `permitAll`),
  nonce com `INSERT ... ON CONFLICT`, cliente de saída (`AplicativoHttp`) e o job
  que entrega o `evento_saida`. `GET /integracao/v1/produtos/{produto}/direitos`
  lista só contratações já provisionadas. `POST /contratacoes/{id}/suporte` e
  `POST /contratacoes/{id}/tentar-provisionamento` são do operador.
- `comercial/` — cliente, produto, recurso, plano, preço, adicional, contratação,
  direitos, cobrança manual e `evento_saida`. O job diário gera cobranças e marca
  `INADIMPLENTE`; não bloqueia. Provisionar espera 1 min, 5 min, 15 min e 1 h e
  então fica `ERRO`; cancelada antes de chegar ao app não é provisionada (evento `DESCARTADO`). O webhook de direitos espera até 72 h e então fica `FALHOU`.
- Fora de `/auth/**` e `/integracao/**`, a rota exige operador autenticado.
- Profile `dev` importa `application-dev-local.yml` (gitignorado). Segredos sem valor padrão.
- CSRF: domínio do `XSRF-TOKEN` em `CENTRAL_CSRF_COOKIE_DOMAIN` (painel e API em subdomínios =
  domínio pai; obrigatória em produção). Variáveis de produção sem valor padrão, conferidas por
  `ConfiguracaoProducaoTest`.
- Idempotency-Key é sempre o id da contratação. Nome, slug e admin só mudam antes de um envio que
  possa ter criado a instância (`Contratacao.dadosDeProvisionamentoEditaveis`, V003); senão 409
  `PROVISIONAMENTO_NAO_EDITAVEL`.
- Testes: `mvn clean verify` (Docker, Postgres 17 — mesma versão major da produção). Ao somar migration, atualizar o total
  em `FlywayMigrationIntegrationTest`.
