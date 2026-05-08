# HIT 3 — Fan-out Simultáneo a Loki + Elasticsearch

Este hit extiende el pipeline del Hit #2 para que el **mismo log record** salga simultáneamente a **Loki** (backend de la Parte 1) y **Elasticsearch** (backend de la Parte 2), demostrando la propuesta de valor clave de OTel: cambiar o agregar backends es editar un YAML, no reescribir agentes.

## Qué se modificó respecto al Hit #2

| Sección del CRD | Hit #2 | Hit #3 |
|---|---|---|
| `exporters` | solo `debug` | `otlphttp/loki` + `elasticsearch` + `debug` (básico) |
| `processors` | `k8sattributes, attributes, batch` | agrega `transform` (genera `log_id` UUID) |
| `spec.env` | — | inyecta `ELASTIC_PASSWORD` desde secret |
| Pipeline | `exporters: [debug]` | `exporters: [otlphttp/loki, elasticsearch]` |

## Por qué `otlphttp/loki` y no el exporter `loki` legacy

Loki ≥ 3.0 (mid-2024) acepta **OTLP nativo** en el endpoint `/otlp`, preservando la estructura completa de OTel (atributos de resource, scope, log record). El exporter `loki` (legacy) está deprecado en `0.110+` y tiene problemas de cardinality al convertir atributos en labels. Se usa `otlphttp/loki`.

## El campo `log_id` — cómo funciona la verificación

El processor `transform` (OTTL — OpenTelemetry Transformation Language) ejecuta:

```yaml
transform:
  log_statements:
    - context: log
      statements:
        - 'set(attributes["log_id"], UUID()) where attributes["log_id"] == nil'
```

Esto asigna un UUID único **antes** de que el record se divida hacia los dos exporters. El mismo `log_id` llega a Loki y a Elasticsearch — la prueba de que el fan-out funciona y no hay duplicación.

## Cómo verificar el fan-out

### 1 — Aplicar el manifest actualizado

```bash
# Solo si no ejecutás el install.sh completo:
kubectl get secret elastic-credentials -n elastic -o yaml \
  | sed 's/namespace: elastic/namespace: otel/' \
  | grep -v 'creationTimestamp\|resourceVersion\|uid\|selfLink' \
  | kubectl apply -f -

kubectl apply -f otel/manifests/collector-agent.yaml
kubectl -n otel rollout status ds/agent-collector --timeout=120s
```

### 2 — Disparar tráfico del scraper

```bash
kubectl -n ml-scraper create job --from=cronjob/scraper-hourly scraper-fanout-1
kubectl -n ml-scraper wait --for=condition=complete job/scraper-fanout-1 --timeout=600s
```

### 3 — Buscar en Loki (Grafana → Explore)

```logql
{service="scraper", k8s_namespace_name="ml-scraper"} | json | line_format "{{.log_id}} {{.message}}"
```

Copiar un `log_id` del output (ej: `aa1b2c3d-...`).

### 4 — Buscar el mismo `log_id` en Kibana (Discover → `scraper-logs-*`)

```
log_id : "aa1b2c3d-..."
```

**El mismo evento debe aparecer en los dos backends.**

## Diagnóstico si algo falla

| Síntoma | Causa probable | Solución |
|---|---|---|
| Solo aparece en Loki | Exporter de Elastic falla | `kubectl -n otel logs ds/agent-collector \| grep -i elastic` — buscar `tls verify failed` o `unauthorized`. Verificar el secret. |
| Solo aparece en Elastic | Endpoint OTLP de Loki mal configurado | Verificar que Loki sea ≥ 3.0 (chart `6.16+`). Verificar que el endpoint sea `:3100/otlp`. |
| Ninguno | Pipeline no arrancó | `kubectl -n otel logs ds/agent-collector \| grep -i error` |
| `unknown exporter "loki"` | Imagen es `core`, no `contrib` | La imagen debe ser `otel/opentelemetry-collector-contrib:0.110.0` |

## Output esperado

```
# Loki — mismo log_id visible en Grafana
ResourceLog #0
  ...
  LogRecord #0
    Attributes:
      -> log_id: Str(aa1b2c3d-4e5f-6789-abcd-ef0123456789)
      -> k8s.namespace.name: Str(ml-scraper)
      -> service: Str(scraper)

# Kibana — mismo log_id en index scraper-logs-*
{
  "log_id": "aa1b2c3d-4e5f-6789-abcd-ef0123456789",
  "k8s.job.name": "scraper-fanout-1",
  ...
}
```

## Screenshots requeridos

| Archivo | Contenido |
|---|---|
| `otel/screenshots/hit3-fanout-loki.png` | Grafana mostrando el log con `log_id` |
| `otel/screenshots/hit3-fanout-elastic.png` | Kibana mostrando el **mismo** `log_id` |
