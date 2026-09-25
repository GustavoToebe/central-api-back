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
Java 21 · Spring Boot 4.1.1 · Hibernate 7 · PostgreSQL 16 (Supabase) · Flyway ·
Testcontainers · Maven. Mesmas convenções do `servire-api-back` (DTOs `record`,
`ApiException`, `@Transactional` no service, javadoc explicando o porquê).
A Central **não** é multi-tenant: sem `@TenantId` e sem `TenantContext`.

## Onde está o código
- `web/` — `ApiError` (com `codigo`), `ApiException`, `GlobalExceptionHandler`, `RequestIdFilter`.
- `security/` — JWT próprio (`CENTRAL_JWT_SEGREDO`), BCrypt, cookie httpOnly de refresh.
- `operador/` — login, refresh, logout, `GET /operadores/eu`. Seed só no profile `dev`,
  se `CENTRAL_OPERADOR_SEED_EMAIL` e `CENTRAL_OPERADOR_SEED_SENHA` existirem.
- `integracao/` — `HmacAssinatura`, filtro de `/integracao/**` (não é `permitAll`),
  nonce com `INSERT ... ON CONFLICT`. `GET /integracao/v1/saude` é a sonda da fundação;
  os endpoints do contrato ainda não existem.
- `comercial/` — cliente, produto, recurso, plano, preço, adicional, contratação,
  direitos, cobrança manual e `evento_saida`. O job diário gera cobranças e marca
  `INADIMPLENTE`; não bloqueia. O job que **envia** o `evento_saida` ainda não existe.
- Fora de `/auth/**` e `/integracao/**`, a rota exige operador autenticado.
- Profile `dev` importa `application-dev-local.yml` (gitignorado). Segredos sem valor padrão.
- Testes: `mvn clean verify` (Docker, Postgres 16). Ao somar migration, atualizar o total
  em `FlywayMigrationIntegrationTest`.
