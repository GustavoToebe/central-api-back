# Backup e restauração do banco — T12

Scripts: `deploy/backup-banco.sh`, `deploy/restaurar-banco.sh`. Teste automático: `deploy/testes/backup.test.sh` (dois Postgres descartáveis em Docker, roda também na CI como aviso, sem bloquear o deploy).

## O que o backup é
`pg_dump` lógico em formato custom dos schemas `public` e `private` (o Servirea guarda funções e gatilhos em `private`), sem dono e sem privilégios, com `.sha256` ao lado. Só vira "backup" depois de ser relido por `pg_restore --list` e ter tabelas com dados. Roda dentro da imagem `postgres:17-alpine`, então a VPS não precisa ter o cliente do Postgres.

```
PGHOST=... PGPORT=5432 PGUSER=... PGPASSWORD=... PGDATABASE=... deploy/backup-banco.sh /opt/ecossistema/backups
```
`BACKUP_RETENCAO_DIAS=14` apaga os mais antigos da pasta (padrão: não apaga nada). Um por banco (Servirea e Central). Agende fora do horário de uso, por exemplo uma linha no cron do root: `15 3 * * * cd /opt/ecossistema/central-api-back && PGHOST=... deploy/backup-banco.sh`. **Nada é agendado por este repositório.**

## O que NÃO está no backup
- Schemas `auth` e `storage` do Supabase e os papéis do banco: o provedor cuida deles. As fotos ficam no Storage, fora do banco, e precisam de cópia própria.
- As chaves AES do MFA (keyring) e os segredos dos `.env`: ficam em cofre, com backup separado. **Sem a chave os segredos de MFA restaurados não decifram.**
- O arquivo do backup deve sair da VPS (outro provedor ou outro disco). Backup só na máquina que falhou não serve.

## Restaurar
```
PGHOST=... PGUSER=... PGPASSWORD=... PGDATABASE=banco_novo deploy/restaurar-banco.sh arquivo.dump [--substituir]
```
Confere o checksum antes de tudo, **recusa** banco que já tem tabelas (a menos que `--substituir`) e restaura numa transação só: se algo falhar, o banco fica como estava. Ao final mostra o número de tabelas e a última migration do Flyway.

Roteiro de emergência:
1. Pare as APIs (`docker compose stop servirea-api central-api`).
2. Restaure no banco novo (de preferência outro nome, para comparar antes de trocar).
3. Se os papéis do banco (T16, [papeis-banco](papeis-banco.md)) estiverem adotados, reaplique `adotar-papeis-banco.sql` e os GRANTs: o backup não leva dono nem privilégio.
4. Aponte `DB_URL` para o banco restaurado, suba as APIs e confira: login do operador na Central, login numa paróquia no Servirea, uma consulta de pessoas e a versão dos direitos da instância.
5. Só depois libere o acesso. Se o código da release atual conhece migrations que o backup não tem, o Flyway migra para a frente ao subir; se o backup tem migrations que o código não conhece, volte o código antes (`rollback.sh`).

## Ensaio de 02/10/2026 (máquina local, Postgres 17 em Docker)
Origem: os bancos locais de desenvolvimento (Servirea V82, 79 tabelas, 170 linhas; Central V16, 28 tabelas, 89 linhas). Destino: outro servidor Postgres vazio.

- Backup e restauração dos dois bancos em poucos segundos. **A contagem de linhas bateu tabela a tabela** nos dois.
- As duas APIs subiram contra os bancos restaurados: o operador entrou com a mesma senha, a contratação `ACESSO_TOTAL` (direitos versão 2) estava lá, e no Servirea o login, as permissões, o snapshot de direitos e as pessoas funcionaram.
- **Defeitos que o ensaio revelou e que já estão corrigidos nos scripts:**
  1. O primeiro backup só levava `public`; sem `private` a restauração do Servirea falhava no primeiro gatilho. Agora leva os dois.
  2. O backup traz `CREATE SCHEMA public`, que quebrava em banco novo. A restauração usa `--clean --if-exists` na mesma transação.
- **Condição do Servirea:** as tabelas dependem do schema `auth` do Supabase (herança das migrations antigas). Num projeto Supabase ele já existe. Num Postgres comum crie antes o que a aplicação espera (os testes usam `src/test/resources/testcontainers/supabase-stubs.sql` do Servirea).

## O que o ensaio NÃO provou
- Nada foi feito contra o **Supabase real**: conexão pelo pooler ou direta, limites de tempo, permissão para `DROP SCHEMA` e criar papéis. Repita em staging ([roteiro](homologacao-staging.md), seção 7).
- Volume: os bancos de teste têm centenas de linhas. Meça tempo e tamanho com dados reais antes de prometer um prazo de recuperação (RTO).
- A restauração junto com os papéis do banco adotados (passo 3 do roteiro) não foi ensaiada.
- Backup agendado, cópia para fora da VPS e alerta de backup que falhou não existem ainda.
