#!/usr/bin/env bash
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "→ Preparación: crear /etc/machine-id en nodo k3d (requerido por Fluent Bit)"
CLUSTER_NAME=$(kubectl config current-context | grep -o 'k3d-[^/]*' | head -1 || echo "k3d-mi-cluster")
CONTAINER_NAME="${CLUSTER_NAME}-server-0"
docker exec "$CONTAINER_NAME" sh -c "[ -f /etc/machine-id ] || echo '$(uuidgen 2>/dev/null || echo f47ac10b58cc4372a5670e02b2c3d479)' > /etc/machine-id" 2>/dev/null || true

echo "→ Namespaces"
kubectl create namespace elastic        --dry-run=client -o yaml | kubectl apply -f -
kubectl create namespace elastic-system --dry-run=client -o yaml | kubectl apply -f -

echo "→ Helm repos"
helm repo add elastic https://helm.elastic.co >/dev/null 2>&1 || true
helm repo add fluent  https://fluent.github.io/helm-charts >/dev/null 2>&1 || true
helm repo update >/dev/null

echo "→ ECK Operator"
helm upgrade --install eck-operator elastic/eck-operator \
  --version 2.16.0 \
  --namespace elastic-system \
  --values "$DIR/helm/eck-operator-values.yaml" \
  --wait --timeout 5m

echo "→ Elasticsearch + Kibana via CRDs"
kubectl apply -f "$DIR/manifests/elasticsearch.yaml"
kubectl apply -f "$DIR/manifests/kibana.yaml"
kubectl apply -f "$DIR/manifests/kibana-nodeport.yaml"

echo "→ Esperando Elasticsearch ready (puede tardar 2-3 min)"
kubectl -n elastic wait --for=jsonpath='{.status.health}'=green elasticsearch/scraper --timeout=300s
kubectl -n elastic wait --for=jsonpath='{.status.health}'=green kibana/scraper --timeout=300s

echo "→ Fluent Bit"
helm upgrade --install fluent-bit fluent/fluent-bit \
  --version 0.48.5 \
  --namespace elastic \
  --values "$DIR/helm/fluent-bit-values.yaml" \
  --wait --timeout 3m

echo "→ ILM policy + index template (Hit #3)"
PASSWORD=$(kubectl -n elastic get secret scraper-es-elastic-user -o jsonpath='{.data.elastic}' | base64 -d)
kubectl -n elastic port-forward svc/scraper-es-http 9200:9200 >/dev/null 2>&1 &
PF_ES=$!
sleep 3

# Activar trial para habilitar connectors premium (webhook, etc.)
# Trial de 30 días — no requiere tarjeta de crédito.
curl -sk -u "elastic:$PASSWORD" \
  -X POST "https://localhost:9200/_license/start_trial?acknowledge=true" \
  -H "Content-Type: application/json" >/dev/null

# Forzar replicas=0 en todos los índices (incluye internos de Kibana).
# Kibana crea sus índices con replicas=1 hardcoded → cluster queda yellow en single-node.
curl -sk -u "elastic:$PASSWORD" -X PUT "https://localhost:9200/_all/_settings" \
  -H "Content-Type: application/json" \
  -d '{"index.number_of_replicas":"0"}' >/dev/null

curl -sk -u "elastic:$PASSWORD" -X PUT "https://localhost:9200/_ilm/policy/scraper-logs" \
  -H "Content-Type: application/json" -d @"$DIR/manifests/ilm-policy.json" >/dev/null

curl -sk -u "elastic:$PASSWORD" -X PUT "https://localhost:9200/_index_template/scraper-logs-template" \
  -H "Content-Type: application/json" -d '{
    "index_patterns": ["scraper-logs-*"],
    "template": {
      "settings": {
        "number_of_shards": 1,
        "number_of_replicas": 0,
        "index.lifecycle.name": "scraper-logs",
        "index.lifecycle.rollover_alias": "scraper-logs"
      }
    }
  }' >/dev/null

kill "$PF_ES" 2>/dev/null || true

echo "→ Dashboard Kibana (Hit #5) — import via Saved Objects API"
kubectl -n elastic port-forward svc/scraper-kb-http 5601:5601 >/dev/null 2>&1 &
PF_KB=$!
sleep 5

curl -sk -u "elastic:$PASSWORD" \
  -X POST "https://localhost:5601/api/saved_objects/_import?overwrite=true" \
  -H "kbn-xsrf: true" \
  -F file=@"$DIR/dashboards/scraper-overview.ndjson" | jq '.success'
# Esperado: true

kill "$PF_KB" 2>/dev/null || true

# ---------------------------------------------------------------------------
# Hit #6 (bonus +5%) — Kibana Alerting: conector Discord + regla de alerta
# Requiere licencia trial (activada arriba) para el connector .webhook.
# Solo se ejecuta si DISCORD_WEBHOOK_URL está definido.
# ---------------------------------------------------------------------------
if [ -n "${DISCORD_WEBHOOK_URL:-}" ]; then
  echo "→ Hit #6 (bonus) — Kibana Alerting: conector Discord + regla de alerta"

  kubectl -n elastic port-forward svc/scraper-kb-http 5601:5601 >/dev/null 2>&1 &
  PF_KB6=$!
  sleep 5

  # Crear conector Webhook (Discord acepta webhooks genéricos POST+JSON)
  CONNECTOR_ID=$(curl -sk -u "elastic:$PASSWORD" \
    -X POST "https://localhost:5601/api/actions/connector" \
    -H "kbn-xsrf: true" \
    -H "Content-Type: application/json" \
    -d "{
      \"name\": \"discord-sip2026\",
      \"connector_type_id\": \".webhook\",
      \"config\": {
        \"url\": \"${DISCORD_WEBHOOK_URL}\",
        \"method\": \"post\",
        \"headers\": { \"Content-Type\": \"application/json\" }
      }
    }" | jq -r '.id')

  echo "  Connector creado — ID: $CONNECTOR_ID"

  # Crear la regla: más de 5 ERRORs en 1h, chequeo cada 5m
  # Notas de implementación:
  #   - searchType: "esQuery" (Kibana 8.17 — "kql" fue removido en 8.x)
  #   - frequency.notify_when: "onActiveAlert" — dispara cada evaluación mientras activa
  #   - {{context.value}} = conteo de hits (usar en vez de {{context.hits}} que es el doc raw)
  curl -sk -u "elastic:$PASSWORD" \
    -X POST "https://localhost:5601/api/alerting/rule" \
    -H "kbn-xsrf: true" \
    -H "Content-Type: application/json" \
    -d "{
      \"name\": \"Scraper: mas de 5 ERRORs en 1h\",
      \"rule_type_id\": \".es-query\",
      \"consumer\": \"alerts\",
      \"schedule\": { \"interval\": \"5m\" },
      \"params\": {
        \"index\": [\"scraper-logs-*\"],
        \"timeField\": \"@timestamp\",
        \"searchType\": \"esQuery\",
        \"esQuery\": \"{\\\"query\\\":{\\\"term\\\":{\\\"level.keyword\\\":\\\"ERROR\\\"}}}\",
        \"size\": 100,
        \"threshold\": [5],
        \"thresholdComparator\": \">\",
        \"timeWindowSize\": 1,
        \"timeWindowUnit\": \"h\"
      },
      \"actions\": [
        {
          \"id\": \"${CONNECTOR_ID}\",
          \"group\": \"query matched\",
          \"frequency\": {
            \"notify_when\": \"onActiveAlert\",
            \"throttle\": null,
            \"summary\": false
          },
          \"params\": {
            \"body\": \"{\\\"content\\\": \\\"ALERTA SIP 2026 (EFK): {{context.value}} errores del scraper en 1h.\\\"}\"
          }
        }
      ]
    }" | jq '.id'
  # Esperado: "<uuid-de-la-regla>"

  kill "$PF_KB6" 2>/dev/null || true
else
  echo "-- Hit #6 omitido (DISCORD_WEBHOOK_URL no definido)"
fi

NODE_IP=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}')
echo ""
echo "✓ ECK Operator running"
echo "✓ Elasticsearch green (trial license activa)"
echo "✓ Kibana available"
echo "✓ Fluent Bit DaemonSet ready"
echo "✓ ILM policy 'scraper-logs' aplicada"
echo "✓ Index template asociado"
echo "✓ Index pattern 'scraper-logs-*' creado"
echo "✓ Dashboard 'Scraper Overview' provisionado"
echo "→ Abrir https://${NODE_IP}:30001   (elastic / \$(kubectl -n elastic get secret scraper-es-elastic-user -o jsonpath='{.data.elastic}' | base64 -d))"
