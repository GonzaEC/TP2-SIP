# HIT 4 — Reemplazar Promtail/Alloy + Fluent Bit por solo OTel Collector

Este hit demuestra el momento "wow" del TP: **eliminar los agentes de logging legacy** sin perder ningún log, porque el OTel Collector ya cubre ambos backends simultáneamente.

## Qué se hizo

| Acción | Comando |
|---|---|
| Escalar Promtail a 0 pods | `kubectl -n observability patch daemonset promtail ...` |
| Verificar que no queda ningún agente legacy | `kubectl get ds -A` |
| Disparar Job de prueba solo con OTel | `kubectl -n ml-scraper create job --from=cronjob/scraper-hourly scraper-otel-only-1` |
| Verificar logs en Loki | Label `k8s_job_name=scraper-otel-only-1` aparece en Loki |
| Verificar logs en Elastic | Index `scraper-logs` crece de 46 → 136 documentos |

> **Nota sobre Fluent Bit:** En este stack EFK el OTel Collector ya era el único agente enviando a Elasticsearch (ECK no incluye Fluent Bit por defecto). Solo Promtail requirió ser bajado.

## Estado antes → después

```
ANTES (HIT 3):
  observability/promtail      DESIRED=1  (activo pero redundante)
  otel/agent-collector        DESIRED=1  (OTel haciendo fan-out)

DESPUÉS (HIT 4):
  observability/promtail      DESIRED=0  (frío, rollback en 30s)
  otel/agent-collector        DESIRED=1  (único agente activo)
```

## Por qué `patch` y no `helm uninstall`

```bash
# ✅ Correcto — el agent queda "frío", rollback en ~30s:
kubectl -n observability patch daemonset promtail \
  --type=merge \
  --patch-file otel/manifests/_patch-promtail-zero.json

# ❌ Destructivo — reinstalar tarda minutos:
# helm uninstall promtail -n observability
```

En producción, el patrón es siempre el mismo: dejar el sistema viejo "frío" durante el período de bake (1-2 semanas), y solo después desinstalar.

## Rollback de emergencia

```bash
# Volver a activar Promtail en 30 segundos:
kubectl -n observability patch daemonset promtail \
  --type=merge \
  --patch '{\"spec\":{\"template\":{\"spec\":{\"nodeSelector\":{}}}}}'
```

## Verificación del flujo sin agentes legacy

### Loki — job `scraper-otel-only-1` visible

```bash
kubectl run curl-loki --image=curlimages/curl --restart=Never --rm -it \
  -n observability --command -- \
  curl -sg "http://loki:3100/loki/api/v1/label/k8s_job_name/values"
# Esperado: ["scraper-fanout-hit3","scraper-otel-only-1",...]
```

### Elasticsearch — index `scraper-logs` creciendo

```bash
kubectl run curl-es --image=curlimages/curl --restart=Never --rm -it \
  -n elastic --command -- \
  curl -sk -u "elastic:$PASS" \
  "https://scraper-es-http.elastic.svc.cluster.local:9200/_cat/indices/scraper-logs?v"
# Esperado: docs.count > 46 (creciendo después del nuevo job)
```

## Output esperado del Hit #4

```
# kubectl get ds -A
NAMESPACE       NAME                     DESIRED   CURRENT   READY
kube-system     svclb-traefik-...        1         1         1
observability   loki-canary              1         1         1
observability   promtail                 0         0         0     ← frío
otel            agent-collector          1         1         1     ← único agente activo

# Loki label values
{"status":"success","data":["scraper-fanout-hit3","scraper-otel-only-1"]}

# Elasticsearch
scraper-logs   docs.count: 136
```

## Screenshot requerido

`otel/screenshots/hit4-old-agents-down.png` — debe mostrar:
- Promtail con `DESIRED=0`
- OTel Collector con `DESIRED=1`
- Logs del job `scraper-otel-only-1` en Loki o Kibana
