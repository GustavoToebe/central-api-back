#!/usr/bin/env bash
# Ensaio automático de backup e restauração (T12) com dois Postgres descartáveis em Docker.
# Uso: bash deploy/testes/backup.test.sh   (precisa de Docker; não toca em nada fora dos contêineres e de uma pasta temporária)
#
# Cobre o que o ensaio manual de 02/10/2026 descobriu: o schema private entra no backup, o backup restaura em banco vazio, as
# contagens batem tabela a tabela, o histórico do Flyway volta, e o restaurador RECUSA banco com dados e arquivo adulterado.
set -euo pipefail

AQUI="$(cd "$(dirname "$0")" && pwd)"
DEPLOY="$(cd "$AQUI/.." && pwd)"
SUFIXO="$$"
REDE="ensaio-backup-$SUFIXO"
ORIGEM="ensaio-origem-$SUFIXO"
DESTINO="ensaio-destino-$SUFIXO"
PASTA="$(mktemp -d)"
IMAGEM="${PG_IMAGEM:-postgres:17-alpine}"

limpar() {
  docker rm -f "$ORIGEM" "$DESTINO" >/dev/null 2>&1 || true
  docker network rm "$REDE" >/dev/null 2>&1 || true
  rm -rf "$PASTA"
}
trap limpar EXIT

falhar() { echo "FALHOU: $*"; exit 1; }
ok() { echo "ok - $*"; }

docker network create "$REDE" >/dev/null
for c in "$ORIGEM" "$DESTINO"; do
  docker run -d --name "$c" --network "$REDE" -e POSTGRES_PASSWORD=ensaio "$IMAGEM" >/dev/null
done
for c in "$ORIGEM" "$DESTINO"; do
  until docker exec "$c" pg_isready -U postgres >/dev/null 2>&1; do sleep 1; done
done
sleep 2
for c in "$ORIGEM" "$DESTINO"; do docker exec "$c" psql -U postgres -qc "create database app" >/dev/null; done

sql() { docker exec -i "$1" psql -U postgres -d app -v ON_ERROR_STOP=1 -q -tA "${@:2}"; }

# Origem com o formato do Servirea: tabela em public, função em private usada por gatilho, histórico do Flyway e dados.
sql "$ORIGEM" <<'SQL' >/dev/null
create schema private;
create function private.set_updated_at() returns trigger language plpgsql as $$ begin new.atualizado_em = now(); return new; end $$;
create table public.pessoa (id serial primary key, nome text not null, atualizado_em timestamptz default now());
create trigger trg_pessoa before update on public.pessoa for each row execute function private.set_updated_at();
create table public.flyway_schema_history (installed_rank int primary key, version text, success boolean not null);
insert into public.flyway_schema_history values (1, '1', true), (2, '2', true), (3, '17', true);
insert into public.pessoa (nome) select 'Pessoa ' || g from generate_series(1, 250) g;
SQL

export PGHOST="$ORIGEM" PGUSER=postgres PGPASSWORD=ensaio PGDATABASE=app PG_DOCKER_REDE="$REDE" PG_IMAGEM="$IMAGEM"
bash "$DEPLOY/backup-banco.sh" "$PASTA" >"$PASTA/saida-backup.txt" || { cat "$PASTA/saida-backup.txt"; falhar "backup"; }
ARQUIVO="$(ls "$PASTA"/app-*.dump)"
[ -f "$ARQUIVO.sha256" ] || falhar "sem checksum"
ok "backup gerado com checksum"

export PGHOST="$DESTINO"
bash "$DEPLOY/restaurar-banco.sh" "$ARQUIVO" >"$PASTA/saida-restauracao.txt" || { cat "$PASTA/saida-restauracao.txt"; falhar "restauração em banco vazio"; }
grep -q "última migration do Flyway: V17" "$PASTA/saida-restauracao.txt" || { cat "$PASTA/saida-restauracao.txt"; falhar "histórico do Flyway não voltou"; }
ok "restaurou em banco vazio e o Flyway voltou na V17"

[ "$(sql "$DESTINO" -c "select count(*) from public.pessoa")" = "250" ] || falhar "contagem de pessoas diferente"
[ "$(sql "$DESTINO" -c "select count(*) from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='private' and p.proname='set_updated_at'")" = "1" ] \
  || falhar "a função do schema private não voltou"
sql "$DESTINO" -c "update public.pessoa set nome = 'Alterada' where id = 1" >/dev/null
[ "$(sql "$DESTINO" -c "select nome from public.pessoa where id = 1")" = "Alterada" ] || falhar "gatilho/função private não funciona após restaurar"
ok "dados, schema private e gatilho funcionando no banco restaurado"

if bash "$DEPLOY/restaurar-banco.sh" "$ARQUIVO" >"$PASTA/saida-recusa.txt" 2>&1; then falhar "restaurou por cima de um banco com dados"; fi
grep -q "Recusado" "$PASTA/saida-recusa.txt" || falhar "mensagem de recusa ausente"
[ "$(sql "$DESTINO" -c "select nome from public.pessoa where id = 1")" = "Alterada" ] || falhar "a recusa alterou o banco"
ok "recusa restaurar em banco com dados, sem mexer nele"

bash "$DEPLOY/restaurar-banco.sh" "$ARQUIVO" --substituir >/dev/null || falhar "--substituir"
[ "$(sql "$DESTINO" -c "select nome from public.pessoa where id = 1")" = "Pessoa 1" ] || falhar "--substituir não voltou ao estado do backup"
ok "--substituir devolve o banco ao estado do backup"

printf 'x' >> "$ARQUIVO"
if bash "$DEPLOY/restaurar-banco.sh" "$ARQUIVO" --substituir >"$PASTA/saida-adulterado.txt" 2>&1; then falhar "restaurou arquivo adulterado"; fi
grep -q "Checksum não confere" "$PASTA/saida-adulterado.txt" || falhar "mensagem de checksum ausente"
[ "$(sql "$DESTINO" -c "select nome from public.pessoa where id = 1")" = "Pessoa 1" ] || falhar "arquivo adulterado alterou o banco"
ok "arquivo adulterado é recusado antes de tocar no banco"

echo "Todos os ensaios de backup e restauração passaram."
