#!/usr/bin/env bash
# Restaura um backup feito por backup-banco.sh em um banco Postgres (T12).
# Uso:  PGHOST=... PGPORT=5432 PGUSER=... PGPASSWORD=... PGDATABASE=... deploy/restaurar-banco.sh arquivo.dump [--substituir]
#   arquivo.dump  precisa ter o arquivo .sha256 ao lado, que é conferido antes de qualquer coisa
#   --substituir  apaga o schema public do banco de destino antes de restaurar (padrão: recusa se o banco já tiver tabelas)
# Variáveis opcionais: PG_IMAGEM, PG_DOCKER_REDE (ver backup-banco.sh).
#
# Restaura numa única transação: se algo falhar, o banco de destino fica como estava. Para ensaiar, aponte para um banco
# DESCARTÁVEL (outro servidor ou outro nome), nunca para a produção. Depois: reaplique os papéis do banco se estiverem adotados
# (docs/papeis-banco.md), suba a API contra o banco restaurado e confira login e dados antes de dizer que o backup serve.
set -euo pipefail

ARQUIVO="${1:?Informe o arquivo .dump}"
SUBSTITUIR=0
[ "${2:-}" = "--substituir" ] && SUBSTITUIR=1
: "${PGHOST:?Defina PGHOST}" "${PGUSER:?Defina PGUSER}" "${PGPASSWORD:?Defina PGPASSWORD}" "${PGDATABASE:?Defina PGDATABASE}"
export PGPORT="${PGPORT:-5432}"
IMAGEM="${PG_IMAGEM:-postgres:17-alpine}"
REDE="${PG_DOCKER_REDE:-host}"

[ -f "$ARQUIVO" ] || { echo "Arquivo não encontrado: $ARQUIVO"; exit 1; }
[ -f "$ARQUIVO.sha256" ] || { echo "Sem $ARQUIVO.sha256: não dá para confirmar que o backup está íntegro."; exit 1; }
( cd "$(dirname "$ARQUIVO")" && sha256sum --check --quiet "$(basename "$ARQUIVO").sha256" ) \
  || { echo "Checksum não confere: o arquivo mudou ou está corrompido. Nada foi restaurado."; exit 1; }

pg() {
  docker run --rm -i --network "$REDE" -e PGHOST -e PGPORT -e PGUSER -e PGPASSWORD -e PGDATABASE "$IMAGEM" "$@"
}

EXISTENTES=$(pg psql -tA -v ON_ERROR_STOP=1 -c "select count(*) from information_schema.tables where table_schema='public'" | tr -d '[:space:]')
if [ "$EXISTENTES" != "0" ]; then
  if [ "$SUBSTITUIR" != "1" ]; then
    echo "O banco $PGDATABASE já tem $EXISTENTES tabelas em public. Recusado. Use um banco vazio ou --substituir (apaga o schema public)."
    exit 1
  fi
  echo "--substituir: o schema public de $PGDATABASE será apagado e recriado na mesma transação da restauração."
fi

echo "Restaurando $(basename "$ARQUIVO") em $PGDATABASE ($PGHOST:$PGPORT)..."
INICIO=$(date +%s)
# --clean --if-exists: o backup traz CREATE SCHEMA public; apagar e recriar na mesma transação evita o erro "já existe" e,
# se algo falhar, o banco continua como estava.
pg pg_restore --clean --if-exists --no-owner --no-privileges --exit-on-error --single-transaction --dbname="$PGDATABASE" < "$ARQUIVO"
pg psql -q -v ON_ERROR_STOP=1 -c "grant usage on schema public to public" > /dev/null
FIM=$(date +%s)

TABELAS=$(pg psql -tA -c "select count(*) from information_schema.tables where table_schema='public'" | tr -d '[:space:]')
VERSAO=$(pg psql -tA -c "select coalesce(max(version::int), 0) from flyway_schema_history where success" 2>/dev/null | tr -d '[:space:]' || echo "?")
echo "OK: $TABELAS tabelas em public, última migration do Flyway: V${VERSAO:-?}, $((FIM - INICIO)) s."
