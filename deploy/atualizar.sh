#!/usr/bin/env bash
# Atualiza a produção: puxa a main dos quatro repositórios, compila os fronts
# (Node na imagem oficial, sem instalar nada na VPS) e recria as APIs.
# Cada imagem das APIs recebe como tag o commit que a gerou e, ao final, o manifesto da release
# fica em releases/ (atual.json e anterior.json) para o rollback.sh. Ver deploy/README.md, seção 9.
# Uso, na VPS: /opt/ecossistema/central-api-back/deploy/atualizar.sh
set -euo pipefail

BASE="${ECOSSISTEMA_BASE:-/opt/ecossistema}"
LOCK="${ECOSSISTEMA_LOCK:-/var/lock/ecossistema-deploy.lock}"

# Só um deploy por vez na VPS. Os quatro repositórios chamam este script, e o GitHub só enfileira
# dentro de cada repositório. Quem chega enquanto outro roda espera até 30 minutos.
exec 9>"$LOCK"
if ! flock -w 1800 9; then
  echo "Outro deploy está rodando há mais de 30 minutos. Nada foi alterado."
  exit 1
fi

cd "$BASE"

for repo in servirea-api-back servirea-api-front central-api-back central-api-front; do
  # Depois de um rollback os repositórios ficam em HEAD solto; voltar para a main antes do pull.
  git -C "$repo" checkout main
  git -C "$repo" pull --ff-only
done

# Tags imutáveis das imagens: o commit curto. O mesmo commit sempre produz a mesma tag.
export SERVIREA_API_TAG="$(git -C servirea-api-back rev-parse --short=12 HEAD)"
export CENTRAL_API_TAG="$(git -C central-api-back rev-parse --short=12 HEAD)"

compilar_front() { # $1 repositório  $2 pasta do dist  $3 destino em sites/
  docker run --rm -v "$BASE/$1:/app" -w /app node:24-alpine sh -c "npm ci --no-audit --no-fund && npx ng build"
  rm -rf "$BASE/sites/$3.novo"
  cp -r "$BASE/$1/dist/$2/browser" "$BASE/sites/$3.novo"
  rm -rf "$BASE/sites/$3"
  mv "$BASE/sites/$3.novo" "$BASE/sites/$3"
}

mkdir -p sites releases
compilar_front servirea-api-front paroquia-escalas-front servirea
compilar_front central-api-front central-api-front central

cd central-api-back/deploy
docker compose up -d --build --wait --wait-timeout 180
docker image prune -f
docker compose ps

# Manifesto da release que acabou de subir com saúde. Falha ao registrar não desfaz o deploy.
cd "$BASE"
NOVO="releases/$(date -u +%Y%m%dT%H%M%SZ).json"
if SERVIREA_API_IMAGEM="servirea-api:$SERVIREA_API_TAG" CENTRAL_API_IMAGEM="central-api:$CENTRAL_API_TAG" \
   bash central-api-back/deploy/manifesto.sh > "$NOVO.tmp"; then
  mv "$NOVO.tmp" "$NOVO"
  # Só vira "anterior" se algo mudou de verdade (o horário do manifesto não conta).
  if [ -f releases/atual.json ] && ! diff -q <(grep -v geradoEm releases/atual.json) <(grep -v geradoEm "$NOVO") >/dev/null; then
    cp releases/atual.json releases/anterior.json
  fi
  cp "$NOVO" releases/atual.json
  echo "Release registrada em $NOVO"
else
  rm -f "$NOVO.tmp"
  echo "Aviso: não foi possível registrar o manifesto da release."
fi
