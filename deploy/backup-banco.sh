#!/usr/bin/env bash
# Backup lógico de um banco Postgres (schemas public e private) em formato custom, com checksum e verificação de leitura (T12).
# Uso:  PGHOST=... PGPORT=5432 PGUSER=... PGPASSWORD=... PGDATABASE=... deploy/backup-banco.sh [pasta-destino]
#   pasta-destino  padrão: $ECOSSISTEMA_BACKUPS ou /opt/ecossistema/backups
# Variáveis opcionais:
#   PG_IMAGEM               imagem com pg_dump/pg_restore (padrão postgres:17-alpine; use a mesma versão maior do servidor ou superior)
#   PG_DOCKER_REDE          rede Docker usada para alcançar o banco (padrão host, o que serve para a VPS e o Supabase)
#   BACKUP_RETENCAO_DIAS    se definida, apaga backups .dump/.sha256 desta pasta com mais de N dias (padrão: não apaga nada)
#
# O que entra: os schemas da aplicação que existirem, public e private (tabelas, funções, o histórico do Flyway; o Servirea guarda
# funções e gatilhos em private). Sem dono e sem privilégios, para
# poder restaurar em outro servidor; depois de restaurar, reaplique os papéis do banco (docs/papeis-banco.md) se estiverem adotados.
# O que NÃO entra: auth/storage do Supabase, papéis, as chaves AES do MFA e os segredos (ficam em cofre, com backup separado).
# Sem a chave, os segredos de MFA restaurados não decifram. Guarde o backup do banco longe da VPS.
set -euo pipefail

: "${PGHOST:?Defina PGHOST}" "${PGUSER:?Defina PGUSER}" "${PGPASSWORD:?Defina PGPASSWORD}" "${PGDATABASE:?Defina PGDATABASE}"
export PGPORT="${PGPORT:-5432}"
IMAGEM="${PG_IMAGEM:-postgres:17-alpine}"
REDE="${PG_DOCKER_REDE:-host}"
DESTINO="${1:-${ECOSSISTEMA_BACKUPS:-/opt/ecossistema/backups}}"

pg() { # comando [args]: roda a ferramenta do Postgres dentro do contêiner, com a conexão por variável de ambiente
  docker run --rm -i --network "$REDE" -e PGHOST -e PGPORT -e PGUSER -e PGPASSWORD -e PGDATABASE "$IMAGEM" "$@"
}

mkdir -p "$DESTINO"
CARIMBO="$(date -u +%Y%m%d-%H%M%S)"
ARQUIVO="$DESTINO/$PGDATABASE-$CARIMBO.dump"
PARCIAL="$ARQUIVO.parcial"
trap 'rm -f "$PARCIAL"' EXIT

echo "Backup de $PGDATABASE em $PGHOST:$PGPORT..."
INICIO=$(date +%s)
SCHEMAS=$(pg psql -tA -c "select string_agg('--schema=' || nspname, ' ' order by nspname) from pg_namespace where nspname in ('public', 'private')" | tr -d '\r')
[ -n "$SCHEMAS" ] || { echo "O banco não tem o schema public."; exit 1; }
# shellcheck disable=SC2086  # SCHEMAS é uma lista de opções
pg pg_dump --format=custom --no-owner --no-privileges $SCHEMAS > "$PARCIAL"
[ -s "$PARCIAL" ] || { echo "Backup vazio: nada foi gravado."; exit 1; }

# Prova que o arquivo é legível e tem tabelas, antes de chamá-lo de backup.
TABELAS=$(pg pg_restore --list < "$PARCIAL" | grep -c " TABLE DATA " || true)
[ "$TABELAS" -gt 0 ] || { echo "Backup ilegível ou sem dados de tabela."; exit 1; }

mv "$PARCIAL" "$ARQUIVO"
( cd "$DESTINO" && sha256sum "$(basename "$ARQUIVO")" > "$(basename "$ARQUIVO").sha256" )
FIM=$(date +%s)

echo "OK: $ARQUIVO ($(wc -c < "$ARQUIVO") bytes, $TABELAS tabelas com dados, $((FIM - INICIO)) s)"

if [ -n "${BACKUP_RETENCAO_DIAS:-}" ]; then
  find "$DESTINO" -maxdepth 1 \( -name "$PGDATABASE-*.dump" -o -name "$PGDATABASE-*.dump.sha256" \) -mtime +"$BACKUP_RETENCAO_DIAS" -print -delete \
    | sed 's/^/Removido por retenção: /'
fi
