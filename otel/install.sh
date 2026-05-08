#!/usr/bin/env bash
set -euo pipefail

NAMESPACE=otel
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "→ Verificando pre-requisitos: Loki (namespace observability) + EFK (namespace elastic)"
kubectl get ns observability >/dev/null 2>&1 || { echo "ERROR: namespace 'observability' no existe. Ejecutar TP2·Parte1 primero."; exit 1; }
kubectl get ns elastic       >/dev/null 2>&1 || { echo "ERROR: namespace 'elastic' no existe. Ejecutar TP2·Parte2 primero."; exit 1; }

echo "→ Namespaces"
kubectl apply -f "$DIR/manifests/namespace.yaml"

echo "→ Helm repo"
helm repo add open-telemetry https://open-telemetry.github.io/opentelemetry-helm-charts >/dev/null 2>&1 || true
helm repo add jetstack https://charts.jetstack.io >/dev/null 2>&1 || true
helm repo update >/dev/null

echo "→ cert-manager (si no está)"
# El operator requiere cert-manager para los webhooks.
kubectl get ns cert-manager >/dev/null 2>&1 || helm install cert-manager jetstack/cert-manager \
  --namespace cert-manager --create-namespace \
  --version v1.16.1 --set installCRDs=true --wait --timeout 5m
kubectl -n cert-manager rollout status deploy/cert-manager-webhook --timeout=120s

echo "→ OpenTelemetry Operator"
helm upgrade --install otel-operator open-telemetry/opentelemetry-operator \
  --version 0.74.0 \
  --namespace otel-operator-system --create-namespace \
  --values "$DIR/helm/otel-operator-values.yaml" \
  --wait --timeout 5m

echo "→ RBAC"
kubectl apply -f "$DIR/manifests/rbac.yaml"

# === HIT 3: copiar el secret de Elastic al namespace otel ===
echo "→ Copiando secret elastic-credentials al namespace otel"
kubectl get secret elastic-credentials -n elastic -o yaml \
  | sed 's/namespace: elastic/namespace: otel/' \
  | grep -v 'creationTimestamp\|resourceVersion\|uid\|selfLink' \
  | kubectl apply -f -

echo "→ Verificando secret en namespace otel"
kubectl -n otel get secret elastic-credentials -o jsonpath='{.data.password}' | base64 -d | grep -c . >/dev/null \
  && echo "   ✓ Secret elastic-credentials disponible en namespace otel" \
  || { echo "ERROR: secret elastic-credentials no disponible en namespace otel"; exit 1; }

echo "→ OpenTelemetryCollector CRD (Hit #3: fan-out Loki + Elasticsearch)"
kubectl apply -f "$DIR/manifests/collector-agent.yaml"
# Esperar a que el operator reconcilie y el daemonset esté listo
sleep 15
kubectl -n "$NAMESPACE" rollout status ds/agent-collector --timeout=300s

echo ""
echo "✓ OpenTelemetry Operator running    (kubectl get pod -n otel-operator-system)"
echo "✓ OpenTelemetryCollector CRD aplicado (kubectl get otelcol -n otel)"
echo "✓ Collector DaemonSet running con 1 pod por nodo"
echo "✓ Secret elastic-credentials copiado al namespace otel"
echo "✓ Pipeline activo: filelog + otlp → k8sattributes → attributes → transform → batch → [otlphttp/loki, elasticsearch]"
echo ""
echo "→ Verificá fan-out:"
echo "   Grafana  → http://<node-ip>:30000  (Explore → Loki → {service=\"scraper\"} | json | log_id)"
echo "   Kibana   → http://<node-ip>:30001  (Discover → index scraper-logs-* → buscar log_id)"
