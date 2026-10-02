#!/usr/bin/env bash
# Teste do manifesto e do rollback em um sandbox: quatro repositórios git de mentira, docker e flock simulados.
# Uso: bash deploy/testes/release.test.sh   (não toca em /opt nem em Docker de verdade)
set -euo pipefail

AQUI="$(cd "$(dirname "$0")" && pwd)"
DEPLOY="$(cd "$AQUI/.." && pwd)"
SANDBOX="$(mktemp -d)"
trap 'rm -rf "$SANDBOX"' EXIT

export ECOSSISTEMA_BASE="$SANDBOX/base"
export ECOSSISTEMA_LOCK="$SANDBOX/lock"
mkdir -p "$ECOSSISTEMA_BASE" "$SANDBOX/bin"
export GIT_AUTHOR_NAME=t GIT_AUTHOR_EMAIL=t@t GIT_COMMITTER_NAME=t GIT_COMMITTER_EMAIL=t@t
export GIT_CONFIG_COUNT=1 GIT_CONFIG_KEY_0=core.autocrlf GIT_CONFIG_VALUE_0=false

# docker e flock de mentira: registram a chamada. "image inspect" responde conforme IMAGENS_EXISTEM.
cat > "$SANDBOX/bin/docker" <<'XEOF'
#!/usr/bin/env bash
echo "docker $*" >> "$LOG"
if [ "$1" = "image" ] && [ "$2" = "inspect" ]; then
  [ "${IMAGENS_EXISTEM:-sim}" = "sim" ] && { echo "sha256:abc"; exit 0; } || exit 1
fi
exit 0
XEOF
cat > "$SANDBOX/bin/flock" <<'XEOF'
#!/usr/bin/env bash
exit 0
XEOF
chmod +x "$SANDBOX/bin/docker" "$SANDBOX/bin/flock"
export PATH="$SANDBOX/bin:$PATH"
export LOG="$SANDBOX/chamadas.log"
: > "$LOG"

falhas=0
confere() { # descrição, condição (0 = ok)
  if [ "$2" = 0 ]; then echo "ok   - $1"; else echo "FALHA - $1"; falhas=$((falhas+1)); fi
}

criar_repo() { # nome, com_migrations
  local d="$ECOSSISTEMA_BASE/$1"
  mkdir -p "$d" && git -C "$d" init -q -b main
  if [ "$2" = sim ]; then mkdir -p "$d/src/main/resources/db/migration"; echo "select 1;" > "$d/src/main/resources/db/migration/V009__a.sql"; echo "select 1;" > "$d/src/main/resources/db/migration/V010__b.sql"; fi
  mkdir -p "$d/deploy"; echo "name: teste" > "$d/deploy/docker-compose.yml"
  echo v1 > "$d/arquivo"; git -C "$d" add -A; git -C "$d" commit -q -m v1
}
commit() { echo "$2" > "$ECOSSISTEMA_BASE/$1/arquivo"; git -C "$ECOSSISTEMA_BASE/$1" commit -qam "$2"; }
sha() { git -C "$ECOSSISTEMA_BASE/$1" rev-parse HEAD; }

for r in servirea-api-front central-api-front; do criar_repo "$r" nao; done
for r in servirea-api-back central-api-back; do criar_repo "$r" sim; done
mkdir -p "$ECOSSISTEMA_BASE/releases" "$ECOSSISTEMA_BASE/sites"
# O docker de mentira não compila: deixa pronta a saída que o build do front produziria.
mkdir -p "$ECOSSISTEMA_BASE/servirea-api-front/dist/paroquia-escalas-front/browser" "$ECOSSISTEMA_BASE/central-api-front/dist/central-api-front/browser"

# --- manifesto
SERVIREA_API_IMAGEM="servirea-api:x" bash "$DEPLOY/manifesto.sh" > "$ECOSSISTEMA_BASE/releases/anterior.json"
grep -q "\"servirea-api-back\": {\"sha\": \"$(sha servirea-api-back)\"" "$ECOSSISTEMA_BASE/releases/anterior.json"; confere "manifesto traz o commit do back do Servirea" $?
grep -q '"migrationUltima": "V010__b.sql"' "$ECOSSISTEMA_BASE/releases/anterior.json"; confere "manifesto usa a ordem natural das migrations (V010 depois de V009)" $?
grep -q '"servirea-api-front": {"sha": ".*", "migrationUltima": ""' "$ECOSSISTEMA_BASE/releases/anterior.json"; confere "front não declara migration" $?
grep -q '"imagem": "sha256:abc"' "$ECOSSISTEMA_BASE/releases/anterior.json"; confere "manifesto traz o id da imagem quando existe" $?

SHA_ANT_SERV="$(sha servirea-api-back)"; SHA_ANT_FRONT="$(sha servirea-api-front)"

# --- release nova (código novo, mesma migration)
for r in servirea-api-back servirea-api-front central-api-back central-api-front; do commit "$r" "v2-$r"; done
bash "$DEPLOY/manifesto.sh" > "$ECOSSISTEMA_BASE/releases/atual.json"

# --- rollback simulado não altera nada
: > "$LOG"
bash "$DEPLOY/rollback.sh" --simular > "$SANDBOX/sim.out"
[ "$(sha servirea-api-back)" != "$SHA_ANT_SERV" ]; confere "simulação não mexe no repositório" $?
grep -q "simulação" "$SANDBOX/sim.out"; confere "simulação descreve as ações" $?
! grep -q "docker compose up" "$LOG"; confere "simulação não sobe containers" $?

# --- rollback de verdade (mesma migration): volta ao commit anterior e usa a imagem existente, sem reconstruir
: > "$LOG"
bash "$DEPLOY/rollback.sh" > "$SANDBOX/real.out"
[ "$(sha servirea-api-back)" = "$SHA_ANT_SERV" ]; confere "rollback volta o back ao commit anterior" $?
[ "$(sha servirea-api-front)" = "$SHA_ANT_FRONT" ]; confere "rollback volta o front ao commit anterior" $?
grep -q "docker compose up -d --no-build --wait" "$LOG"; confere "usa a imagem já construída (sem build)" $?
[ "$(ls "$ECOSSISTEMA_BASE"/releases/revertida-*.json | wc -l)" = 1 ]; confere "guarda o manifesto da release revertida" $?
cmp -s "$ECOSSISTEMA_BASE/releases/atual.json" "$ECOSSISTEMA_BASE/releases/anterior.json"; confere "atual passa a ser a anterior" $?

# --- sem a imagem local, reconstrói
for r in servirea-api-back servirea-api-front central-api-back central-api-front; do git -C "$ECOSSISTEMA_BASE/$r" checkout -q main; done
bash "$DEPLOY/manifesto.sh" > "$ECOSSISTEMA_BASE/releases/atual.json"
: > "$LOG"
IMAGENS_EXISTEM=nao bash "$DEPLOY/rollback.sh" > /dev/null
grep -q "docker compose up -d --build --wait" "$LOG"; confere "imagem ausente: reconstrói do commit" $?

# --- migration avançou: recusa, a menos que aceite
for r in servirea-api-back central-api-back; do git -C "$ECOSSISTEMA_BASE/$r" checkout -q main; done
echo "select 1;" > "$ECOSSISTEMA_BASE/servirea-api-back/src/main/resources/db/migration/V011__c.sql"
git -C "$ECOSSISTEMA_BASE/servirea-api-back" add -A; git -C "$ECOSSISTEMA_BASE/servirea-api-back" commit -qm migration
bash "$DEPLOY/manifesto.sh" > "$ECOSSISTEMA_BASE/releases/atual.json"
cp "$ECOSSISTEMA_BASE/releases/anterior.json" "$ECOSSISTEMA_BASE/releases/anterior.bak"
HEAD_ANTES="$(sha servirea-api-back)"
set +e; bash "$DEPLOY/rollback.sh" > "$SANDBOX/recusa.out"; codigo=$?; set -e
[ "$codigo" = 3 ]; confere "migration nova: rollback recusado (código 3)" $?
grep -q "Rollback recusado" "$SANDBOX/recusa.out"; confere "mensagem explica a recusa" $?
[ "$(sha servirea-api-back)" = "$HEAD_ANTES" ]; confere "recusa não altera o repositório" $?
set +e; bash "$DEPLOY/rollback.sh" --aceitar-migrations > /dev/null; codigo=$?; set -e
[ "$codigo" = 0 ]; confere "--aceitar-migrations permite voltar" $?

# --- sem release anterior
rm -f "$ECOSSISTEMA_BASE/releases/anterior.json"
set +e; bash "$DEPLOY/rollback.sh" > "$SANDBOX/sem.out" 2>&1; codigo=$?; set -e
[ "$codigo" = 1 ]; confere "sem anterior.json: erro claro" $?

echo
if [ "$falhas" = 0 ]; then echo "Todos os testes de release passaram."; else echo "$falhas teste(s) falharam."; exit 1; fi
