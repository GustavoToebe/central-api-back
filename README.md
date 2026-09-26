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

## Ponta a ponta local (Windows, tudo no PC)

Quatro programas e um Postgres no Docker, sem tocar no Supabase. Portas: Servire API 8080, Central API 8081,
Servire front 4200, Central front **4201** (os dois fronts usam 4200 por padrão). O segredo de teste dos
vetores, em Base64, é `c2VncmVkby1kZS10ZXN0ZS1uYW8tdXNhci1lbS1wcm9kdWNhby0wMTIzNDU2Nzg5`
(**só para uso local**, nunca em produção).

Regra de ouro: `$env:X = "..."` só vale no terminal onde foi digitado e só para o programa iniciado **depois**,
nesse mesmo terminal. Cada API tem o seu terminal, com as suas variáveis coladas antes do `mvn`.

**0. Atualizar os quatro repositórios.** Procura cada um em `Documents\servire\` e em `Documents\`:

```powershell
$base = "$env:USERPROFILE\OneDrive\Documents"
foreach ($r in "servire-api-back", "servire-api-front", "central-api-back", "central-api-front") {
  $p = @("$base\servire\$r", "$base\$r") | Where-Object { Test-Path "$_\.git" } | Select-Object -First 1
  if ($p) { Write-Host "== $r ($p)" -ForegroundColor Cyan; git -C $p pull --ff-only }
  else { Write-Host "== $r nao encontrado" -ForegroundColor Red }
}
```

**1. Banco (uma vez; Docker Desktop aberto).** Porta 5433 para não bater com outro Postgres na 5432.

```powershell
docker run --name ecossistema-db -e POSTGRES_PASSWORD=postgres -p 5433:5432 -d postgres:17
docker exec ecossistema-db createdb -U postgres servire_dev
docker exec ecossistema-db createdb -U postgres central_dev
# na pasta do servire-api-back: stub do Supabase (schemas auth/storage e roles) antes do Flyway
Get-Content src\test\resources\testcontainers\supabase-stubs.sql | docker exec -i ecossistema-db psql -U postgres -d servire_dev
```

Container já existe ("name is already in use")? Tudo bem: `docker start ecossistema-db`. Começar do zero:
`docker rm -f ecossistema-db` e repetir.

**2. API do Servire (terminal só dela, pasta `servire-api-back`).** Se houver `application-dev-local.yml`,
ele não pode ter `datasource` (passaria por cima do banco local).

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5433/servire_dev"; $env:DB_PASSWORD="postgres"
$s="c2VncmVkby1kZS10ZXN0ZS1uYW8tdXNhci1lbS1wcm9kdWNhby0wMTIzNDU2Nzg5"
$env:SERVIRE_INTEGRACAO_CHAVES_ENTRADA="teste-central:$s"
$env:SERVIRE_INTEGRACAO_CHAVE_SAIDA_ID="teste-servire"
$env:SERVIRE_INTEGRACAO_CHAVE_SAIDA_SEGREDO=$s
$env:SERVIRE_INTEGRACAO_CENTRAL_URL="http://localhost:8081"
mvn spring-boot:run -DskipTests "-Dspring-boot.run.profiles=dev"
```

No ar quando `http://localhost:8080/actuator/health` mostra `"status":"UP"`.

**3. API da Central (terminal só dela, pasta `central-api-back`).** O operador é criado na subida, só no
profile `dev`, com o e-mail e a senha abaixo. A senha de um operador que já existe **nunca** é trocada: para
outra senha, use outro e-mail.

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5433/central_dev"; $env:DB_PASSWORD="postgres"
$s="c2VncmVkby1kZS10ZXN0ZS1uYW8tdXNhci1lbS1wcm9kdWNhby0wMTIzNDU2Nzg5"
$env:CENTRAL_JWT_SEGREDO=$s
$env:CORS_ALLOWED_ORIGINS="http://localhost:4201"
$env:CENTRAL_OPERADOR_SEED_EMAIL="gustavo2@teste.local"; $env:CENTRAL_OPERADOR_SEED_SENHA="12345678"
$env:CENTRAL_PRODUTO_SERVIRE_CHAVES_ENTRADA="teste-servire:$s"
$env:CENTRAL_PRODUTO_SERVIRE_CHAVE_SAIDA_ID="teste-central"
$env:CENTRAL_PRODUTO_SERVIRE_CHAVE_SAIDA_SEGREDO=$s
mvn spring-boot:run -DskipTests "-Dspring-boot.run.profiles=dev"
```

**4. Fronts.** `servire-api-front`: `npm start` (http://localhost:4200). `central-api-front`:
`npm start -- --port 4201` (http://localhost:4201). Depois de um `git pull` que mexeu no `package.json`,
rode `npm ci` antes.

**Primeiro acesso.** Central: login com o e-mail e a senha do passo 3. Servire: não há usuário pronto; o
primeiro nasce do convite da contratação (passo 2 abaixo). Com o provedor de e-mail `log`, o link aparece no
terminal da API do Servire (`[STUB] Convite para ...`); com o Resend no `application-dev-local.yml`, chega no
e-mail de verdade.

**Problemas já vistos**

| Sintoma | Causa |
|---|---|
| "E-mail ou senha inválidos" na Central | variáveis do seed coladas em outro terminal, ou operador já criado com outra senha |
| "Entrega ... adiada ... Falha de rede" no log da Central | API do Servire fora do ar ou URL do produto errada (tem que ser `http://localhost:8080`, não 4200); a Central tenta de novo em 1, 5, 15 min e 1 h |
| Contratação em "Aguardando envio" | normal por até 1 minuto; a tela não se atualiza sozinha (F5) |

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
