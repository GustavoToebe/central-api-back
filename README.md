# Central — API

Plataforma central de gestão comercial do ecossistema de aplicativos SaaS
(Servire hoje; academia e finanças no futuro): clientes, produtos, planos,
adicionais, contratações, cobranças e direitos de uso. Cada aplicativo
continua dono dos próprios usuários, perfis, permissões e dados de negócio;
a Central só diz **quem é cliente, o que contratou e se pode usar**.

Front do painel do operador: [`central-api-front`](https://github.com/GustavoToebe/central-api-front).

## Estado atual

**Fundação, domínio comercial e integração com os apps.** Operadores (login, refresh em cookie httpOnly, logout),
JWT próprio e filtro HMAC de `/integracao/**`. Cadastros de cliente, produto, recurso, plano, preço e adicional;
contratação com direitos, histórico e cobrança manual. O job diário gera cobranças e marca `INADIMPLENTE`.
O job do outbox (a cada minuto) provisiona a instância e envia o webhook de direitos.
`GET /integracao/v1/produtos/{produto}/direitos` devolve só o que já tem `id_externo`.
O operador pede suporte (`POST /contratacoes/{id}/suporte`) e, se o provisionamento parou em `ERRO`,
`POST /contratacoes/{id}/tentar-provisionamento`.

Para subir em dev: Postgres local, profile `dev`, e `application-dev-local.yml`
(gitignorado) ou as variáveis do `.env.example`. Sem `CENTRAL_JWT_SEGREDO` e sem
`DB_PASSWORD` a API não sobe. O seed do operador só roda no profile `dev`, e só
se o e-mail e a senha vierem no ambiente.

| Documento | Conteúdo |
|---|---|
| [`docs/arquitetura-central.md`](docs/arquitetura-central.md) | objetivo, responsabilidades, modelo, provisionamento, sincronização, mudanças no Servire (perfis/usuários no estilo SIN+), plano de etapas, riscos e decisões |
| [`docs/contrato-integracao-v1.md`](docs/contrato-integracao-v1.md) | contrato técnico Central ↔ apps: HMAC (com vetores de teste), erros, snapshot de direitos, endpoints |

## Ponta a ponta local

Central na porta 8081 e Servire na 8080, cada um com o seu Postgres. O segredo de teste dos vetores, em Base64, é `c2VncmVkby1kZS10ZXN0ZS1uYW8tdXNhci1lbS1wcm9kdWNhby0wMTIzNDU2Nzg5` (não usar em produção).

No Servire:

- `SERVIRE_INTEGRACAO_CHAVES_ENTRADA=teste-central:c2VncmVkby1kZS10ZXN0ZS1uYW8tdXNhci1lbS1wcm9kdWNhby0wMTIzNDU2Nzg5`
- `SERVIRE_INTEGRACAO_CHAVE_SAIDA_ID=teste-servire`
- `SERVIRE_INTEGRACAO_CHAVE_SAIDA_SEGREDO` com o mesmo Base64
- `SERVIRE_INTEGRACAO_CENTRAL_URL=http://localhost:8081`
- provedor de e-mail `log` (o convite sai no log, em DEBUG: `[STUB] Convite`)

Na Central:

- `CENTRAL_PRODUTO_SERVIRE_CHAVES_ENTRADA=teste-servire:` seguido do mesmo Base64
- `CENTRAL_PRODUTO_SERVIRE_CHAVE_SAIDA_ID=teste-central`
- `CENTRAL_PRODUTO_SERVIRE_CHAVE_SAIDA_SEGREDO` com o mesmo Base64

No painel, ou pela API com o token do operador:

1. Cadastre o produto com código `SERVIRE` e URL base `http://localhost:8080`. O Servire pede exatamente esse código na sincronização.
2. Cadastre cliente, plano e uma contratação com slug e e-mail do administrador. Ela nasce `PENDENTE`. Em até um minuto o job faz `POST /integracao/v1/instancias`. A paróquia aparece no Servire e o convite vai para o log.
3. Bloqueie a contratação. O job manda `PUT .../direitos` e o Servire passa a recusar o acesso na hora.
4. Pare a Central. O Servire continua na cópia local. Sem novo webhook, a regra das 72 h (`SERVIRE_INTEGRACAO_TOLERANCIA_HORAS`, padrão 72) bloqueia a paróquia. O log de erro da sincronização avisa a partir de 24 h.

Rede, timeout e 5xx no provisionamento esperam 1 min, 5 min, 15 min e 1 h, e então a contratação fica `ERRO`. 409 e 422 não repetem. Contratação cancelada antes de chegar ao app não é mais enviada: a instância não é criada e o histórico mostra o descarte. `POST /contratacoes/{id}/tentar-provisionamento` reenvia com a mesma `Idempotency-Key`, que é o id da contratação. Nome, slug e administrador só podem ser editados antes do primeiro envio ou depois de uma recusa 4xx que não seja 409 (`provisionamentoEditavel` na resposta). O webhook de direitos espera até 72 h e então o evento fica `FALHOU`, com alerta no log.

## Próximos passos

Etapas 1 e 2 (Servire), 3 (esta API) e 4 (`central-api-front`) prontas — ver
`docs/arquitetura-central.md`, seção 14.

1. Etapa 5 — corte em produção: projeto Supabase da Central (Postgres 17), variáveis de produção
   (`.env.example`, incluindo `CENTRAL_CSRF_COOKIE_DOMAIN`), recriar o banco do Servire.

## Stack prevista

Java 21 · Spring Boot 4.1.1 · Hibernate 7 · PostgreSQL 17 (Supabase) · Flyway ·
Testcontainers · Maven — a mesma do `servire-api-back`, para reaproveitar
código e convenções.
