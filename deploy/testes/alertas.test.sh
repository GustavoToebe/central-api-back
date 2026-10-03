#!/usr/bin/env bash
# Teste dos alertas (T14) com as ferramentas e imagens oficiais, sem tocar na VPS.
# Uso: bash deploy/testes/alertas.test.sh   (precisa de Docker e das imagens fixadas no docker-compose.monitoramento.yml)
#
# 1. promtool valida prometheus.yml e alertas.yml e roda alertas.teste.yml (cada alerta dispara e não dispara quando deve).
# 2. amtool valida alertmanager.yml e confere o ROTEAMENTO real (todo alerta vai para a equipe).
# 3. Entrega de ponta a ponta: Prometheus (alvo morto) -> Alertmanager -> receptor HTTP; e um alerta manual pelo amtool.
#    A entrega usa webhook para um receptor local; o envio real por e-mail (Resend) só se prova em staging com a chave verdadeira.
set -euo pipefail

AQUI="$(cd "$(dirname "$0")" && pwd)"
MON="$(cd "$AQUI/../monitoramento" && pwd)"
PROM="${PROM_IMAGEM:-prom/prometheus:v3.5.0}"
ALERTA="${ALERTMANAGER_IMAGEM:-prom/alertmanager:v0.28.1}"
PY="${PY_IMAGEM:-python:3.13-alpine}"
SUF="$$"
REDE="ensaio-alertas-$SUF"
TMP="$(mktemp -d)"

win() { (cd "$1" && (pwd -W 2>/dev/null || pwd)); }  # caminho que o Docker entende, no Windows e no Linux
export MSYS_NO_PATHCONV=1

limpar() {
  docker rm -f "am-$SUF" "pr-$SUF" "rc-$SUF" >/dev/null 2>&1 || true
  docker network rm "$REDE" >/dev/null 2>&1 || true
  rm -rf "$TMP"
}
trap limpar EXIT
falhar() { echo "FALHOU: $*"; exit 1; }
ok() { echo "ok - $*"; }

MON_W="$(win "$MON")"
mkdir -p "$TMP/segredos"
for s in servirea_monitoramento central_monitoramento alertmanager_smtp; do printf 'segredo-de-teste' > "$TMP/segredos/$s"; chmod 644 "$TMP/segredos/$s"; done
SEG_W="$(win "$TMP/segredos")"

# 1. Prometheus: configuração, regras e testes das regras.
docker run --rm -v "$MON_W:/etc/prometheus:ro" -v "$SEG_W:/run/secrets:ro" --entrypoint promtool "$PROM" check config /etc/prometheus/prometheus.yml >"$TMP/c.txt" 2>&1 \
  || { cat "$TMP/c.txt"; falhar "promtool check config"; }
ok "prometheus.yml válido (inclui o apontamento para o Alertmanager)"
docker run --rm -v "$MON_W:/w:ro" -w /w --entrypoint promtool "$PROM" test rules alertas.teste.yml >"$TMP/t.txt" 2>&1 \
  || { cat "$TMP/t.txt"; falhar "promtool test rules"; }
ok "regras testadas: cada alerta dispara e não dispara quando deve (alertas.teste.yml)"
for nome in $(sed -n 's/^ *- alert: *//p' "$MON/alertas.yml"); do
  grep -q "alertname: $nome" "$MON/alertas.teste.yml" || falhar "o alerta $nome não tem caso em alertas.teste.yml"
done
ok "todo alerta de alertas.yml tem teste"

# 2. Alertmanager: configuração e roteamento.
docker run --rm -v "$MON_W:/etc/alertmanager:ro" -v "$SEG_W:/run/secrets:ro" --entrypoint amtool "$ALERTA" check-config /etc/alertmanager/alertmanager.yml >"$TMP/a.txt" 2>&1 \
  || { cat "$TMP/a.txt"; falhar "amtool check-config"; }
ok "alertmanager.yml válido"
for rotulos in "severity=warning alertname=ApiErrosElevados job=central" "severity=info alertname=InstanciasSemResumoAtual job=central"; do
  R="$(docker run --rm -v "$MON_W:/etc/alertmanager:ro" -v "$SEG_W:/run/secrets:ro" --entrypoint amtool "$ALERTA" config routes test --config.file=/etc/alertmanager/alertmanager.yml $rotulos | tr -d '\r')"
  [ "$R" = "equipe" ] || falhar "roteamento de [$rotulos] foi para [$R], esperado equipe"
done
ok "roteamento: warning e info vão para a equipe"
if grep -q "COMPLETAR" "$MON/alertmanager.yml"; then
  echo "AVISO: alertmanager.yml ainda tem marcadores COMPLETAR (remetente e destinatário). Troque antes de ligar em produção."
fi

# 3. Entrega de ponta a ponta com um receptor HTTP local.
docker network create "$REDE" >/dev/null
docker run -d --name "rc-$SUF" --network "$REDE" "$PY" python -u -c '
import http.server
class H(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        corpo = self.rfile.read(int(self.headers.get("content-length", 0))).decode()
        open("/tmp/recebido.log", "a").write(corpo + "\n")
        self.send_response(200); self.end_headers()
    def log_message(self, *a): pass
http.server.HTTPServer(("", 8080), H).serve_forever()
' >/dev/null

cat > "$TMP/am-teste.yml" <<'YML'
route:
  receiver: receptor
  group_by: [alertname]
  group_wait: 1s
  group_interval: 5s
  repeat_interval: 1h
receivers:
  - name: receptor
    webhook_configs:
      - url: http://RECEPTOR:8080/
YML
sed -i "s/RECEPTOR/rc-$SUF/" "$TMP/am-teste.yml"
docker run -d --name "am-$SUF" --network "$REDE" -v "$(win "$TMP"):/cfg:ro" "$ALERTA" --config.file=/cfg/am-teste.yml --storage.path=/tmp/am >/dev/null

cat > "$TMP/prom-teste.yml" <<'YML'
global:
  scrape_interval: 2s
  evaluation_interval: 2s
rule_files:
  - /cfg/regra-teste.yml
alerting:
  alertmanagers:
    - static_configs:
        - targets: [AM:9093]
scrape_configs:
  - job_name: central
    static_configs:
      - targets: [alvo-que-nao-existe:9]
YML
sed -i "s/AM:9093/am-$SUF:9093/" "$TMP/prom-teste.yml"
# Mesma regra ApiSemColeta de alertas.yml, só com `for: 0s` para o teste não esperar 3 minutos.
cat > "$TMP/regra-teste.yml" <<'YML'
groups:
  - name: teste
    rules:
      - alert: ApiSemColeta
        expr: up{job=~"servirea|central"} == 0
        for: 0s
        labels: {severity: warning}
YML
docker run -d --name "pr-$SUF" --network "$REDE" -v "$(win "$TMP"):/cfg:ro" "$PROM" --config.file=/cfg/prom-teste.yml --storage.tsdb.path=/tmp/p >/dev/null

esperar_recebido() { # texto, segundos
  for _ in $(seq 1 "$2"); do
    if docker exec "rc-$SUF" sh -c "grep -q '$1' /tmp/recebido.log 2>/dev/null"; then return 0; fi
    sleep 1
  done
  return 1
}
esperar_recebido "ApiSemColeta" 60 || { docker logs "pr-$SUF" 2>&1 | tail -5; docker logs "am-$SUF" 2>&1 | tail -5; falhar "o alerta do Prometheus não chegou ao receptor"; }
ok "Prometheus detectou o alvo morto, o Alertmanager recebeu e entregou ao receptor"

until docker exec "am-$SUF" wget -q -O /dev/null http://127.0.0.1:9093/-/ready 2>/dev/null; do sleep 1; done
docker exec "am-$SUF" amtool alert add --alertmanager.url=http://127.0.0.1:9093 alertname=TesteManualDeEntrega severity=warning job=teste >/dev/null
esperar_recebido "TesteManualDeEntrega" 30 || falhar "o alerta manual não chegou ao receptor"
ok "alerta manual (amtool) entregue"

echo "Todos os testes de alertas passaram."
