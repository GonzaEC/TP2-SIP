# TP 2 · Parte 3 — OpenTelemetry

Este módulo centraliza la recolección de telemetría (logs, traces) utilizando **OpenTelemetry Collector** como agente vendor-neutral que hace fan-out simultáneo a Loki y Elasticsearch.

## Arquitectura

```
[Pods del scraper]
     │  stdout → CRI → /var/log/pods/
     │  (filelog receiver)
     │
     ▼
[OTel Collector — DaemonSet]
  receivers:  filelog, otlp
  processors: k8sattributes → attributes → transform(log_id) → batch
  exporters:
     ├──► Loki (otlphttp/loki → :3100/otlp)   [Grafana]
     └──► Elasticsearch (:9200)                [Kibana]
```

## Pre-requisitos

- Stack Loki + Grafana (Parte 1) operativo en namespace `observability`.
- Stack EFK (Elasticsearch + Kibana) (Parte 2) operativo en namespace `elastic`.
- `cert-manager` instalado (requerido por el OTel Operator para los admission webhooks).

## Instalación

```bash
cd otel && ./install.sh
```

El script:
1. Verifica que Loki y EFK estén corriendo.
2. Instala `cert-manager` si no está presente.
3. Instala el **OTel Operator** via Helm (versión `0.74.0`).
4. **Copia el secret `elastic-credentials`** del namespace `elastic` al namespace `otel`.
5. Aplica el `OpenTelemetryCollector` CRD con fan-out a Loki + Elastic.

## Verificación Hit #2 — Pipeline básico (debug exporter)

```bash
kubectl -n otel get otelcol        # NAME=agent, MODE=daemonset, READY=1/1
kubectl -n otel logs ds/agent-collector | grep ResourceLog
```

## Verificación Hit #3 — Fan-out Loki + Elasticsearch

### Disparar tráfico

```bash
kubectl -n ml-scraper create job --from=cronjob/scraper-hourly scraper-fanout-1
kubectl -n ml-scraper wait --for=condition=complete job/scraper-fanout-1 --timeout=600s
```

### Loki (Grafana → Explore)

```logql
{service="scraper", k8s_namespace_name="ml-scraper"} | json | line_format "{{.log_id}} {{.message}}"
```

### Elasticsearch (Kibana → Discover → `scraper-logs-*`)

```
log_id : "<valor copiado de Loki>"
```

El mismo evento debe aparecer en los **dos backends** con el mismo `log_id`.

## Estructura de archivos

```
otel/
├── README.md                        ← este archivo
├── helm/
│   └── otel-operator-values.yaml   ← values del chart opentelemetry-operator
├── manifests/
│   ├── namespace.yaml              ← namespace "otel" + "otel-operator-system"
│   ├── rbac.yaml                   ← ServiceAccount + ClusterRole para k8sattributes
│   ├── collector-agent.yaml        ← OpenTelemetryCollector CRD (DaemonSet)
│   └── scraper-otlp-config.yaml    ← ConfigMap con OTEL_EXPORTER_OTLP_ENDPOINT
└── install.sh                      ← script idempotente de instalación completa
```
