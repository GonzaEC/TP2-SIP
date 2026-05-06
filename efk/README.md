# Stack EFK (Elasticsearch + Fluent Bit + Kibana)

Este directorio contiene la infraestructura para el stack de logging EFK del scraper de MercadoLibre, coexistiendo con el stack Loki de la Parte 1 (namespace `observability`). El stack EFK corre en el namespace `elastic`.

## Estructura

```
efk/
├── helm/
│   ├── eck-operator-values.yaml    ← values del chart elastic/eck-operator
│   └── fluent-bit-values.yaml      ← values pinneados de fluent/fluent-bit
├── manifests/
│   ├── namespace.yaml              ← namespace `elastic`
│   ├── elasticsearch.yaml          ← CR Elasticsearch (ECK CRD)
│   ├── kibana.yaml                 ← CR Kibana (ECK CRD)
│   ├── kibana-nodeport.yaml        ← Service NodePort 30001
│   └── ilm-policy.json             ← ILM policy hot→warm→delete (7 días)
├── dashboards/
│   └── scraper-overview.ndjson     ← dashboard exportado as-code (Hit #5)
├── queries/
│   └── kql-cookbook.md             ← 7 queries KQL documentadas (Hit #4)
├── screenshots/
│   ├── Hit1-Output.png
│   ├── hit2-fluentbit-discover.png
│   ├── hit3-ilm-policy.png
│   └── hit5-dashboard.png
└── install.sh                      ← script idempotente con todos los pasos
```

## Pre-requisitos

- Cluster k3s/k3d con al menos **8 GB de RAM libres** y **15 GB de disco**
- `kubectl` ≥ 1.30 y `helm` ≥ 3.16 configurados
- Acceso a Docker (para el fix de `/etc/machine-id` en k3d)
- TP 2 · Parte 1 (Loki) corriendo en namespace `observability`

## Instalación (un solo comando)

```bash
cd efk
chmod +x install.sh
./install.sh
```

> **DISCORD_WEBHOOK_URL** es opcional — solo necesario para el **Hit #6 (bonus)**:
> ```bash
> export DISCORD_WEBHOOK_URL='https://discord.com/api/webhooks/<id>/<token>'
> ./install.sh
> ```
> ⚠️ **No commitear la URL** — es un secret. Si no se define la variable, el script omite el Hit #6 sin fallar.
> La password de `elastic` la genera ECK automáticamente — nunca se commitea.

### Output esperado al final:

```
✓ ECK Operator running
✓ Elasticsearch green
✓ Kibana available
✓ Fluent Bit DaemonSet ready
✓ ILM policy 'scraper-logs' aplicada
✓ Index template asociado
✓ Index pattern 'scraper-logs-*' creado
✓ Dashboard 'Scraper Overview' provisionado
→ Abrir https://<node-ip>:30001   (elastic / <ver secret>)
```

## Acceso

- **Kibana**: `https://<NODE_IP>:30001`
- **Usuario**: `elastic`
- **Password**:
  ```bash
  kubectl -n elastic get secret scraper-es-elastic-user -o jsonpath='{.data.elastic}' | base64 -d
  ```

---

## Hit #1 — ECK Operator + Elasticsearch single-node + Kibana

El script levanta:
1. ECK Operator en namespace `elastic-system` (Helm chart `elastic/eck-operator` v2.16.0)
2. Elasticsearch 8.17.3 via CRD (single-node, `number_of_replicas: 0`)
3. Kibana 8.17.3 via CRD
4. Service NodePort en puerto `30001` para acceso externo

**Verificación:**
```bash
kubectl -n elastic get elasticsearch,kibana
# NAME                                          HEALTH   NODES   VERSION   PHASE   AGE
# elasticsearch.k8s.elastic.co/scraper         green    1       8.17.3    Ready   5m
# kibana.k8s.elastic.co/scraper                green    1       8.17.3    Available  5m
```

Screenshot: `screenshots/Hit1-Output.png`

---

## Hit #2 — Fluent Bit como DaemonSet, pipeline al scraper

Fluent Bit (chart `fluent/fluent-bit` v0.48.5) actúa como agente por nodo. Pipeline:
`Input (tail /var/log/containers/ml-scraper)` → `Parser (CRI + json_scraper)` → `Filter (kubernetes metadata)` → `Output (Elasticsearch TLS)`

**Verificación:**
```bash
# Pod en Running
kubectl -n elastic get pods -l app.kubernetes.io/name=fluent-bit

# Sin errores de conexión a ES
kubectl -n elastic logs -l app.kubernetes.io/name=fluent-bit | grep -i error

# Índice creado con documentos
kubectl -n elastic port-forward svc/scraper-es-http 9200:9200 &
PASSWORD=$(kubectl -n elastic get secret scraper-es-elastic-user -o jsonpath='{.data.elastic}' | base64 -d)
curl -sk -u "elastic:$PASSWORD" "https://localhost:9200/_cat/indices?v" | grep scraper
```

**Disparar tráfico de prueba:**
```bash
kubectl -n ml-scraper create job --from=cronjob/scraper-hourly scraper-efk-test-1
kubectl -n ml-scraper wait --for=condition=complete job/scraper-efk-test-1 --timeout=600s
```

Screenshot: `screenshots/hit2-fluentbit-discover.png` — Kibana → Discover mostrando los campos JSON parseados (`level`, `producto`, `message`, `kubernetes.namespace`, etc.)

---

## Hit #3 — Index pattern + ILM (rollover, retention 7 días)

### ILM Policy

La policy `scraper-logs` define un ciclo **hot 1 día → warm 6 días → delete**:

- **Hot**: rollover al superar 1d o 1GB de shard → índice activo donde Fluent Bit escribe
- **Warm**: shrink a 1 shard, forcemerge a 1 segmento → índice comprimido de solo lectura
- **Delete**: eliminación a los 7 días → equivalente al `retention_period: 168h` de Loki

El `install.sh` aplica automáticamente la policy y el index template vía API:
```bash
curl -sk -u "elastic:$PASSWORD" -X PUT "https://localhost:9200/_ilm/policy/scraper-logs" \
  -H "Content-Type: application/json" -d @manifests/ilm-policy.json
```

### Data View (Index Pattern)

El Data View `scraper-logs` (pattern `scraper-logs-*`, timestamp `@timestamp`) se crea automáticamente al importar el NDJSON del dashboard (Hit #5), ya que está incluido como primer objeto del archivo `dashboards/scraper-overview.ndjson`.

### Forzar rollover en desarrollo

Sin esperar 24h reales:
```bash
curl -sk -u "elastic:$PASSWORD" \
  -X POST "https://localhost:9200/scraper-logs/_rollover" \
  -H "Content-Type: application/json" \
  -d '{"conditions":{"max_age":"0ms"}}'
```

**Verificación en Kibana:** Stack Management → Index Lifecycle Policies → policy `scraper-logs` con 3 fases visible.

Screenshot: `screenshots/hit3-ilm-policy.png`

---

## Hit #4 — KQL Cookbook (6+ queries)

Las queries KQL están documentadas en [`queries/kql-cookbook.md`](queries/kql-cookbook.md).

Incluye 7 queries con pregunta de negocio, query KQL, equivalente Lucene, output esperado y justificación técnica. Al final hay una tabla comparativa KQL vs LogQL (Parte 1).

---

## Hit #5 — Dashboard Kibana provisionado as-code

El dashboard `Scraper Overview` se exportó como NDJSON y se importa automáticamente en `install.sh` via la Saved Objects API:

```bash
curl -sk -u "elastic:$PASSWORD" \
  -X POST "https://localhost:5601/api/saved_objects/_import?overwrite=true" \
  -H "kbn-xsrf: true" \
  -F file=@dashboards/scraper-overview.ndjson
```

> **Nota:** El header `kbn-xsrf: true` es obligatorio — sin él Kibana devuelve 400 aunque la autenticación sea válida.

El NDJSON incluye 8 objetos: data view + 6 visualizaciones + dashboard. Todos los objetos relacionados se importan en un solo request.

**Paneles del dashboard:**
1. **Metric** — Total de eventos hoy
2. **Metric** — % de eventos `level: "ERROR"` vs total
3. **Bar chart** — Top 5 productos con más errores (Q1 del cookbook)
4. **Pie chart** — Distribución por `level` (INFO / WARNING / ERROR)
5. **Line chart** — Eventos por minuto last 6h, breakdown por `level`
6. **Table** — Última corrida exitosa por producto

Screenshot: `screenshots/hit5-dashboard.png`

---

## Hit #6 — Alertas via Kibana Alerting (bonus +5%)

### Variables de entorno requeridas

```bash
export DISCORD_WEBHOOK_URL='https://discord.com/api/webhooks/<id>/<token>'
./install.sh
```

> ⚠️ **No commitear esta URL.** Si la variable no está definida, `install.sh` omite este hit sin fallar.

### Qué automatiza `install.sh`

1. **Conector Discord** (tipo `.webhook` — Kibana no tiene conector Discord nativo, pero Discord acepta webhooks genéricos POST+JSON):
   - Creado via `POST /api/actions/connector`

2. **Regla de alerta** (tipo `.es-query`, KQL):

   | Parámetro | Valor |
   |-----------|-------|
   | Index | `scraper-logs-*` |
   | Time field | `@timestamp` |
   | Query KQL | `level: "ERROR"` |
   | Threshold | `IS ABOVE 5` |
   | Time window | `1h` |
   | Check every | `5m` |

   Mensaje Discord: `ALERTA SIP 2026 (EFK): {{context.hits}} errores del scraper en 1h. Producto top: {{context.value}}`

### Cómo testear sin esperar tráfico real

Opción 1 — bajar el threshold a 0 en Kibana → Stack Management → Rules, guardar, esperar el próximo ciclo de 5m.

Opción 2 — generar 6 jobs que producen errores:
```bash
for i in $(seq 1 6); do
  kubectl -n ml-scraper create job --from=cronjob/scraper-hourly "scraper-err-test-$i"
done
```

Screenshot: `screenshots/hit6-discord-alert-efk.png`
