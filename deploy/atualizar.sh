#!/usr/bin/env bash
# Atualiza a produção: puxa a main dos quatro repositórios, compila os fronts
# (Node na imagem oficial, sem instalar nada na VPS) e recria as APIs.
# Uso, na VPS: /opt/ecossistema/central-api-back/deploy/atualizar.sh
set -euo pipefail

# Só um deploy por vez na VPS. Os quatro repositórios chamam este script, e o GitHub só enfileira
# dentro de cada repositório. Quem chega enquanto outro roda espera até 30 minutos.
exec 9>/var/lock/ecossistema-deploy.lock
if ! flock -w 1800 9; then
  echo "Outro deploy está rodando há mais de 30 minutos. Nada foi alterado."
  exit 1
fi

BASE=/opt/ecossistema
cd "$BASE"

for repo in servirea-api-back servirea-api-front central-api-back central-api-front; do
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
compilar_front servirea-api-front paroquia-escalas-front servirea
compilar_front central-api-front central-api-front central

cd central-api-back/deploy
docker compose up -d --build
docker image prune -f
docker compose ps
