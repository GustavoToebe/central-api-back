# Produção na VPS

Uma VPS Ubuntu, com as duas APIs e o Caddy no Docker. Os bancos ficam no Supabase, na região
ca-central-1, com um projeto para cada sistema. Os fronts são arquivos estáticos servidos pelo Caddy.

| Endereço | O quê | Onde |
|---|---|---|
| `app.servirea.com.br` | Servirea (paróquias) | `sites/servire` |
| `api.servirea.com.br` | API do Servirea | container `servire-api:8080` |
| `central.servirea.com.br` | Painel da Central | `sites/central` |
| `api-central.servirea.com.br` | API da Central | container `central-api:8081` |
| `servirea.com.br`, `www` | redireciona para `app` | Caddy |
| `whatsapp.servirea.com.br` | tela da EvolutionGo (QR code) | container `evolution:4000`, com usuário e senha no Caddy |

Os arquivos desta pasta:
- `docker-compose.yml`: as duas APIs e o Caddy. As APIs não publicam porta nenhuma.
- `Caddyfile`: o HTTPS automático e o proxy para as APIs.
- `atualizar.sh`: faz o `git pull` nos quatro repositórios, compila os fronts e recria os containers.

## 1. DNS (Cloudflare)

Crie registros `A` para `app`, `api`, `central`, `api-central`, `@` e `www`, todos apontando para o IP
da VPS e todos como **DNS only (nuvem cinza)**. Com o proxy do Cloudflare ligado:
- a API receberia o IP do Cloudflare em vez do IP do visitante, e o limite da inscrição pública (por
  IP) passaria a valer para todo mundo junto;
- o Caddy teria mais trabalho para tirar o certificado.

## 2. Supabase (nos dois projetos)

- Ao criar o projeto, deixe **Enable automatic RLS desmarcado**. Essa opção cria `public.rls_auto_enable` e o
  event trigger `ensure_rls`, e com o `public` "não vazio" o Flyway se recusa a começar ("Found non-empty
  schema(s) public but no schema history table"). Se foi marcada, rode no SQL Editor de cada projeto:
  `DROP EVENT TRIGGER IF EXISTS ensure_rls; DROP FUNCTION IF EXISTS public.rls_auto_enable();`
  As migrations já ligam o RLS nas tabelas.
- **Desligue a Data API** (Project Settings → Data API). Só a API Java usa o banco. As migrations
  V039 (Servirea) e V008 (Central) já fecham o schema `public`, e desligar aqui é uma segunda trava.
- **Desligue o cadastro de usuários do Supabase Auth** (Authentication → Sign In / Providers → "Allow
  new users to sign up").
- **Conexão:** use Connect → **Session pooler**, que funciona por IPv4. Copie o host exatamente como o
  painel mostra, por exemplo `aws-0-sa-east-1.pooler.supabase.com`, com a porta 5432. O usuário é
  `postgres.<ref-do-projeto>`.
- **URL JDBC:** `jdbc:postgresql://<host>:5432/postgres?sslmode=require`.
- As tabelas são criadas pelo Flyway na primeira subida de cada API.

## 3. Preparar a VPS (uma vez só)

```bash
apt update && apt upgrade -y
curl -fsSL https://get.docker.com | sh
ufw allow OpenSSH && ufw allow 80/tcp && ufw allow 443/tcp && ufw allow 443/udp && ufw allow 2090/tcp && ufw --force enable
mkdir -p /opt/ecossistema && cd /opt/ecossistema
git clone gh-servirea-api-back:GustavoToebe/servirea-api-back.git servirea-api-back
git clone gh-servirea-api-front:GustavoToebe/servirea-api-front.git servirea-api-front
git clone gh-central-api-back:GustavoToebe/central-api-back.git
git clone gh-central-api-front:GustavoToebe/central-api-front.git
```

Os repositórios são privados. Cada um tem uma deploy key só de leitura, que o GitHub não deixa repetir entre
repositórios. As chaves ficam em `~/.ssh/deploy_<repo>`, com um alias `gh-<repo>` no `~/.ssh/config`, e a
chave pública vai em GitHub → repositório → Settings → Deploy keys.

**VPS com o painel ICP (Integrator Host, Montreal).** O painel traz um nginx próprio (`ic-nginx-*`) nas portas
80 e 443, que só serve a página padrão do servidor. Ele foi parado com `docker update --restart=no` e
`docker stop` para o Caddy assumir essas portas. O painel continua em `:2090`. O Supabase fica em
ca-central-1, na mesma cidade da VPS (sa-east-1 dava 120 ms por conexão).

## 4. Segredos

São dois arquivos, fora do git: `/opt/ecossistema/servirea.env` e `/opt/ecossistema/central.env`,
os dois com `chmod 600`.

Gere cada segredo com `openssl rand -base64 48 | tr -d '\n'` (uma linha só). Ele serve para os JWT
e para as chaves de integração. Nunca reaproveite o mesmo segredo em dois lugares.

**servirea.env.** Parta do `servirea-api-back/.env.example`. As variáveis:

| Variável | Valor |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | dados do Session pooler do projeto Servirea |
| `JWT_SECRET` | um segredo |
| `CORS_ALLOWED_ORIGINS` | `https://app.servirea.com.br` |
| `CSRF_COOKIE_DOMAIN` | `servirea.com.br` |
| `FRONTEND_BASE_URL` | `https://app.servirea.com.br` |
| `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY` | as fotos; podem ficar vazias por enquanto, e só o envio de foto falha |
| `TURNSTILE_SECRET_KEY` | a chave secreta do Turnstile (Cloudflare) |
| `RESEND_API_KEY` | a chave do Resend (o domínio `servirea.com.br` já está verificado) |
| `WHATSAPP_PROVIDER` | `evolution`. Sem ela vale `log`, que não envia nada e mesmo assim responde "enviada" |
| `EVOLUTION_URL` | `http://evolution:4000` (rede interna do compose) |
| `SERVIRE_INTEGRACAO_CHAVES_ENTRADA` | `central:<segredo A>` |
| `SERVIRE_INTEGRACAO_CHAVE_SAIDA_ID` | `servire` |
| `SERVIRE_INTEGRACAO_CHAVE_SAIDA_SEGREDO` | `<segredo B>` |
| `SERVIRE_INTEGRACAO_ALERTA_EMAIL` | o e-mail que recebe o alerta de 24 h sem a Central |

**central.env.** As variáveis:

| Variável | Valor |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | dados do Session pooler do projeto Central |
| `CENTRAL_JWT_SEGREDO` | um segredo |
| `CORS_ALLOWED_ORIGINS` | `https://central.servirea.com.br` |
| `CENTRAL_CSRF_COOKIE_DOMAIN` | `servirea.com.br` (o cookie se chama `CENTRAL-XSRF-TOKEN` e não colide com o do Servirea) |
| `CENTRAL_PRODUTO_SERVIRE_CHAVES_ENTRADA` | `servire:<segredo B>` |
| `CENTRAL_PRODUTO_SERVIRE_CHAVE_SAIDA_ID` | `central` |
| `CENTRAL_PRODUTO_SERVIRE_CHAVE_SAIDA_SEGREDO` | `<segredo A>` |

Como as chaves se cruzam:
- O segredo A é usado pela Central para chamar o Servirea.
- O segredo B é usado pelo Servirea para chamar a Central.

O compose define duas coisas: a `SERVIRE_INTEGRACAO_CENTRAL_URL` (`http://central-api:8081`, pela rede
interna do Docker) e o pool do Hikari, que fica em 5 conexões por API.

## 5. Subir e atualizar

```bash
bash /opt/ecossistema/central-api-back/deploy/atualizar.sh
```

Rode o mesmo comando a cada atualização. Para ver os logs:
`cd /opt/ecossistema/central-api-back/deploy && docker compose logs -f servirea-api`.

## 6. Primeiro acesso

**Operador da Central.** O seed só existe em dev. Em produção, crie o operador pelo `psql`, e não
pelo SQL Editor:

```bash
docker run --rm -it postgres:17-alpine psql "host=<host> port=5432 dbname=postgres user=postgres.<ref-central> sslmode=require"
```

```sql
INSERT INTO operador (id, nome, email, senha_hash)
VALUES (gen_random_uuid(), 'Gustavo', '<e-mail>',
        '{bcrypt}' || extensions.crypt('<senha>', extensions.gen_salt('bf', 10)));
```

**Produto Servirea na Central.** Cadastre o produto com a URL de integração
`http://servirea-api:8080`, que é a rede interna do Docker (o nome antigo `servire-api` continua como alias). Depois cadastre a cliente, a contratação e o
provisionamento da paróquia. O administrador recebe o convite por e-mail.

## 7. WhatsApp (EvolutionGo) e Fail2Ban (27/09/2026)

**EvolutionGo 0.7.2** (`evolution`, com o Postgres próprio `evolution-db`) roda na rede interna. A Central chama
`http://evolution:4000` com o header `apikey`. Para ler o QR code e administrar a conexão, abra
`https://whatsapp.servirea.com.br/manager`, que tem duas travas: o usuário e a senha do Caddy e depois a chave
global da API.

Os segredos, gerados na VPS com `chmod 600`, ficam em quatro arquivos:
- `evolution.env`: `GLOBAL_API_KEY` e as URLs `POSTGRES_*_DB`.
- `evolution-db.env`: `POSTGRES_USER`, `POSTGRES_PASSWORD` e `POSTGRES_DB`.
- `caddy.env`: `EVO_ADMIN_USUARIO` e `EVO_ADMIN_HASH`. O hash é o `caddy hash-password` em **base64**, porque o
  `$` do bcrypt vira variável no compose.
- `acesso-whatsapp.txt`: as credenciais para a primeira leitura. Apague o arquivo depois.

Regras do WhatsApp:
- Use um número **dedicado**, porque não é a API oficial da Meta.
- Mande só para quem autorizou (`autorizaWhatsapp`) e em volume baixo.
- Registro DNS: `whatsapp`, tipo A, nuvem cinza.

**Fail2Ban** (`apt`, `/etc/fail2ban/jail.d/servirea.local`) vigia o SSH:
- 5 erros em 10 minutos bloqueiam o IP por 1 hora, no ufw.
- Quem reincide (`recidive`) fica bloqueado 1 semana.
- Para ver: `fail2ban-client status sshd`.

Não instale apps pela App Store do painel ICP. Vários ligam de novo o nginx dele, que disputa as portas 80 e 443
com o Caddy.

## 8. Deploy automático (GitHub Actions)

Cada um dos quatro repositórios tem `.github/workflows/deploy.yml`. Push na `main` (menos commit só de `.md`) **testa** e, se passar, **publica**: entra na VPS e roda o `atualizar.sh`. O botão "Run workflow" (aba Actions) faz o mesmo à mão.

- **Um deploy por vez.** O GitHub só enfileira dentro de cada repositório, então a trava fica no `atualizar.sh` (`flock` em `/var/lock/ecossistema-deploy.lock`, espera até 30 min).
- **Chave própria do deploy**, sem relação com a chave pessoal de root. No `~/.ssh/authorized_keys` da VPS ela vai numa linha com comando fixo, então só consegue rodar o deploy (e **quem conecta com ela dispara um deploy**, não abre shell):
  ```
  command="bash /opt/ecossistema/central-api-back/deploy/atualizar.sh",no-pty,no-port-forwarding,no-agent-forwarding,no-X11-forwarding ssh-ed25519 <chave pública> github-actions
  ```
- **Secrets** (os mesmos nos quatro repositórios, ou da organização):

| Secret | Valor |
|---|---|
| `VPS_HOST` | `184.107.176.76` |
| `VPS_USER` | `root` |
| `VPS_SSH_KEY` | a chave **privada** do deploy, inteira, com as linhas `BEGIN` e `END` |
| `VPS_KNOWN_HOSTS` | a linha `ed25519` do servidor, vinda do `known_hosts` de um PC confiável (fixa o servidor; o job não confia no primeiro que aparecer) |

Sem os quatro, o job `publicar` falha e a produção não muda. Migration nova roda quando a API sobe: **migration destrutiva vai para produção assim que a `main` passar nos testes.**

## Verificações da branch de melhorias (01/10/2026)

As duas APIs passam a ter healthcheck interno a cada 30 segundos. O script aguarda saúde com `docker compose up --wait`, até 180 segundos, e termina com erro se os serviços não ficarem saudáveis. Isso verifica a API/banco; não substitui ensaio de restauração ou smoke test dos fluxos de negócio. Rollback de release continua uma tarefa separada.

O Caddy limita corpos recebidos pelas quatro entradas de API a 6 MiB, preservando o limite multipart atual. Cada receptor HMAC limita adicionalmente a integração a 1 MiB. Consultar a [diretiva request_body](https://caddyserver.com/docs/caddyfile/directives/request_body). Antes de publicar: validar Caddy/Compose e confirmar saúde em staging. Estas configurações ainda não foram implantadas na VPS.
