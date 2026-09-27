#!/usr/bin/env bash
# Atualiza a produção: puxa a main dos quatro repositórios, compila os fronts
# (Node na imagem oficial, sem instalar nada na VPS) e recria as APIs.
# Uso, na VPS: /opt/ecossistema/central-api-back/deploy/atualizar.sh
set -euo pipefail
BASE=/opt/ecossistema
cd "$BASE"

for repo in servire-api-back servire-api-front central-api-back central-api-front; do
  git -C "$repo" pull --ff-only
done

compilar_front() { # $1 repositório  $2 pasta do dist  $3 destino em sites/
  docker run --rm -v "$BASE/$1:/app" -w /app node:24-alpine sh -c "npm ci --no-audit --no-fund && npx ng build"
  rm -rf "$BASE/sites/$3.novo"
  cp -r "$BASE/$1/dist/$2/browser" "$BASE/sites/$3.novo"
  rm -rf "$BASE/sites/$3"
  mv "$BASE/sites/$3.novo" "$BASE/sites/$3"
}

mkdir -p sites
compilar_front servire-api-front paroquia-escalas-front servire
compilar_front central-api-front central-api-front central

cd central-api-back/deploy
docker compose up -d --build
docker image prune -f
docker compose ps
