#!/usr/bin/env bash
# Imprime o manifesto de release dos quatro componentes: commit, última migration (APIs) e imagem local (APIs).
# Uso: deploy/manifesto.sh            (lê ECOSSISTEMA_BASE, padrão /opt/ecossistema)
# Cada componente fica numa linha só para o rollback.sh ler com sed, sem depender de jq.
set -euo pipefail

BASE="${ECOSSISTEMA_BASE:-/opt/ecossistema}"

sha() { git -C "$BASE/$1" rev-parse HEAD; }

migration() { # última migration versionada do repositório (ordem natural, V010 depois de V009)
  local pasta="$BASE/$1/src/main/resources/db/migration"
  if [ -d "$pasta" ]; then ls "$pasta" | grep -E '^V[0-9]+__' | sort -V | tail -1 || true; fi
}

imagem() { # id da imagem local, vazio se o docker não estiver disponível ou a imagem não existir
  command -v docker >/dev/null 2>&1 || return 0
  docker image inspect "$1" --format '{{.Id}}' 2>/dev/null || true
}

linha() { # nome, tag da imagem (ou vazio), com_migration (sim/não)
  local nome="$1" tag="$2" mig=""
  [ "$3" = "sim" ] && mig="$(migration "$nome")"
  local img=""
  [ -n "$tag" ] && img="$(imagem "$tag")"
  printf '    "%s": {"sha": "%s", "migrationUltima": "%s", "imagem": "%s"}' "$nome" "$(sha "$nome")" "$mig" "$img"
}

printf '{\n  "geradoEm": "%s",\n  "componentes": {\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
linha servirea-api-back "${SERVIREA_API_IMAGEM:-}" sim; printf ',\n'
linha servirea-api-front "" nao; printf ',\n'
linha central-api-back "${CENTRAL_API_IMAGEM:-}" sim; printf ',\n'
linha central-api-front "" nao; printf '\n'
printf '  }\n}\n'
