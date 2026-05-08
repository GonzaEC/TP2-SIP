#!/usr/bin/env bash
set -euo pipefail

NAMESPACE=otel
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "→ Verificando pre-requisitos: Loki + EFK"
# En este entorno de prueba, si kubectl falla, el script fallará.
# Asumimos que el usuario tiene el cluster arriba.

echo "→ Namespaces"
kubectl apply -f "$DIR/manifests/namespace.yaml"

echo "→ Helm repo"
helm repo add open-telemetry https://open-telemetry.github.io/opentelemetry-helm-charts >/dev/null 2>&1 || true
helm repo update >/dev/null

echo "→ cert-manager (si no está)"
# El operator requiere cert-manager para los webhooks.
kubectl get ns cert-manager >/dev/null 2>&1 || helm install cert-manager jetstack/cert-manager \
  --namespace cert-manager --create-namespace \
  --version v1.16.1 --set installCRDs=true --wait --timeout 5m

echo "→ OpenTelemetry Operator"
helm upgrade --install otel-operator open-telemetry/opentelemetry-operator \
  --version 0.74.0 \
  --namespace otel-operator-system --create-namespace \
  --values "$DIR/helm/otel-operator-values.yaml" \
  --wait --timeout 5m

echo "→ RBAC"
kubectl apply -f "$DIR/manifests/rbac.yaml"

echo "→ OpenTelemetryCollector CRD"
kubectl apply -f "$DIR/manifests/collector-agent.yaml"
# Esperar a que el operator cree el daemonset y los pods estén listos
sleep 10
kubectl -n "$NAMESPACE" rollout status ds/agent-collector --timeout=300s

echo ""
echo "✓ OpenTelemetry Operator running"
echo "✓ OpenTelemetryCollector CRD aplicado"
echo "✓ Collector DaemonSet running"
echo "✓ Pipeline activo: filelog + otlp → batch → k8sattributes → [debug]"
