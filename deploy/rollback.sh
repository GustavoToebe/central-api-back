#!/usr/bin/env bash
# Volta a produção para a release anterior registrada em releases/anterior.json (ver atualizar.sh).
# Uso na VPS: deploy/rollback.sh [--simular] [--aceitar-migrations]
#   --simular             só mostra o que faria; nada é alterado
#   --aceitar-migrations  permite voltar o código mesmo que a release atual tenha migrations que a anterior não conhece
#
# Rollback é de CÓDIGO. O banco não volta sozinho (Flyway só avança). Por isso o script recusa voltar quando a release
# atual trouxe migration nova, a menos que você aceite explicitamente (ex.: depois de conferir que a migration é compatível
# ou de restaurar o backup). O próximo deploy da main traz o código de volta para a frente.
set -euo pipefail

BASE="${ECOSSISTEMA_BASE:-/opt/ecossistema}"
LOCK="${ECOSSISTEMA_LOCK:-/var/lock/ecossistema-deploy.lock}"
SIMULAR=0
ACEITAR=0
for a in "$@"; do
  case "$a" in
    --simular) SIMULAR=1 ;;
    --aceitar-migrations) ACEITAR=1 ;;
    *) echo "Opção desconhecida: $a"; exit 2 ;;
  esac
done

exec 9>"$LOCK"
if ! flock -w 1800 9; then
  echo "Outro deploy está rodando há mais de 30 minutos. Nada foi alterado."
  exit 1
fi

cd "$BASE"
ATUAL=releases/atual.json
ANTERIOR=releases/anterior.json
[ -f "$ATUAL" ] || { echo "Sem releases/atual.json: não há release registrada."; exit 1; }
[ -f "$ANTERIOR" ] || { echo "Sem releases/anterior.json: não há release anterior para voltar."; exit 1; }

campo() { # arquivo componente campo
  sed -n "s/.*\"$2\": {.*\"$3\": \"\([^\"]*\)\".*/\1/p" "$1" | head -1
}

executar() {
  if [ "$SIMULAR" = 1 ]; then echo "[simulação] $*"; else "$@"; fi
}

# 1. Compatibilidade com o banco: migration mais nova do que a anterior conhece?
BLOQUEIO=0
for api in servirea-api-back central-api-back; do
  mig_atual="$(campo "$ATUAL" "$api" migrationUltima)"
  mig_ant="$(campo "$ANTERIOR" "$api" migrationUltima)"
  if [ "$mig_atual" != "$mig_ant" ]; then
    echo "$api: a release atual aplicou $mig_atual e a anterior só conhece $mig_ant."
    BLOQUEIO=1
  fi
done
if [ "$BLOQUEIO" = 1 ] && [ "$ACEITAR" != 1 ]; then
  echo "Rollback recusado: o banco já avançou além do código anterior. Confira a migration (e o backup) e repita com --aceitar-migrations se for seguro."
  exit 3
fi

# 2. Código: cada repositório volta ao commit da release anterior.
for repo in servirea-api-back servirea-api-front central-api-back central-api-front; do
  sha="$(campo "$ANTERIOR" "$repo" sha)"
  [ -n "$sha" ] || { echo "Manifesto anterior sem commit para $repo."; exit 1; }
  git -C "$repo" cat-file -e "$sha^{commit}" 2>/dev/null || { echo "Commit $sha de $repo não existe localmente. Nada foi alterado."; exit 1; }
done
for repo in servirea-api-back servirea-api-front central-api-back central-api-front; do
  executar git -C "$repo" checkout --detach "$(campo "$ANTERIOR" "$repo" sha)"
done

# 3. Imagens das APIs: usa a imagem já construída da release anterior; se ela não existe mais, reconstrói do commit.
tag_servirea="$(campo "$ANTERIOR" servirea-api-back sha | cut -c1-12)"
tag_central="$(campo "$ANTERIOR" central-api-back sha | cut -c1-12)"
export SERVIREA_API_TAG="$tag_servirea" CENTRAL_API_TAG="$tag_central"
CONSTRUIR="--no-build"
for par in "servirea-api:$tag_servirea" "central-api:$tag_central"; do
  if ! docker image inspect "$par" >/dev/null 2>&1; then
    echo "Imagem $par não existe mais; será reconstruída do commit anterior."
    CONSTRUIR="--build"
  fi
done

# 4. Fronts: compilados de novo a partir do commit anterior.
compilar_front() { # repositório, pasta do dist, destino em sites/
  executar docker run --rm -v "$BASE/$1:/app" -w /app node:24-alpine sh -c "npm ci --no-audit --no-fund && npx ng build"
  if [ "$SIMULAR" != 1 ]; then
    rm -rf "$BASE/sites/$3.novo"
    cp -r "$BASE/$1/dist/$2/browser" "$BASE/sites/$3.novo"
    rm -rf "$BASE/sites/$3"
    mv "$BASE/sites/$3.novo" "$BASE/sites/$3"
  fi
}
compilar_front servirea-api-front paroquia-escalas-front servirea
compilar_front central-api-front central-api-front central

# 5. Sobe e espera a saúde. Só registra o novo estado se subiu saudável.
cd central-api-back/deploy
executar docker compose up -d $CONSTRUIR --wait --wait-timeout 180
executar docker compose ps
cd "$BASE"
if [ "$SIMULAR" != 1 ]; then
  cp "$ATUAL" "releases/revertida-$(date -u +%Y%m%dT%H%M%SZ).json"
  cp "$ANTERIOR" "$ATUAL"
  echo "Rollback concluído. Servindo os commits de $ANTERIOR. O próximo deploy da main volta para a frente."
else
  echo "Simulação concluída. Nada foi alterado."
fi
