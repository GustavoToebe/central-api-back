# Central — API

Plataforma central de gestão comercial do ecossistema de aplicativos SaaS
(Servire hoje; academia e finanças no futuro): clientes, produtos, planos,
adicionais, contratações, cobranças e direitos de uso. Cada aplicativo
continua dono dos próprios usuários, perfis, permissões e dados de negócio;
a Central só diz **quem é cliente, o que contratou e se pode usar**.

Front do painel do operador: [`central-api-front`](https://github.com/GustavoToebe/central-api-front).

## Estado atual

**Etapa 0 — desenho.** Ainda não há código.

| Documento | Conteúdo |
|---|---|
| [`docs/arquitetura-central.md`](docs/arquitetura-central.md) | objetivo, responsabilidades, modelo, provisionamento, sincronização, mudanças no Servire (perfis/usuários no estilo SIN+), plano de etapas, riscos e decisões |
| [`docs/contrato-integracao-v1.md`](docs/contrato-integracao-v1.md) | contrato técnico Central ↔ apps: HMAC (com vetores de teste), erros, snapshot de direitos, endpoints |

## Próximos passos

1. Etapa 1 — Servire: baseline de migrations novo, perfis e permissões por módulo, usuários e convite.
2. Etapa 2 — Servire: `/integracao/v1`, cópia local de direitos, regra das 72h.
3. Etapa 3 — este repositório: API da Central.
4. Etapa 4 — `central-api-front`.
5. Etapa 5 — corte em produção.

## Stack prevista

Java 21 · Spring Boot 4.1.1 · Hibernate 7 · PostgreSQL 16 (Supabase) · Flyway ·
Testcontainers · Maven — a mesma do `servire-api-back`, para reaproveitar
código e convenções.
