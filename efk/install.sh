#!/usr/bin/env bash
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

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

echo "→ ILM policy + index template"
PASSWORD=$(kubectl -n elastic get secret scraper-es-elastic-user -o jsonpath='{.data.elastic}' | base64 -d)
kubectl -n elastic port-forward svc/scraper-es-http 9200:9200 >/dev/null 2>&1 &
PF_ES=$!
trap "kill $PF_ES 2>/dev/null || true" EXIT
sleep 3

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

# Nota: El import del dashboard (Hit #5) se agregará cuando el archivo .ndjson esté listo.

NODE_IP=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}')
echo ""
echo "✓ ECK Operator running"
echo "✓ Elasticsearch green"
echo "✓ Kibana available"
echo "✓ Fluent Bit DaemonSet ready"
echo "✓ ILM policy 'scraper-logs' aplicada"
echo "✓ Index template asociado"
echo "→ Abrir https://${NODE_IP}:30001   (elastic / \$(kubectl -n elastic get secret scraper-es-elastic-user -o jsonpath='{.data.elastic}' | base64 -d))"
