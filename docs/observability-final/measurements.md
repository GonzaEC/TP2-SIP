# Observability Stack — Mediciones Empíricas

Ventana de medición: **2026-05-08 — 2026-05-09** (24 hs)  
Cluster: k3d `scraper` — 1 server, 0 agents  
Workload generador de logs: `ml-scraper` (CronJob `scraper-hourly`)

---

## Tabla Resumen

| Métrica | Loki + Promtail + Grafana | EFK (ES + Fluent Bit + Kibana) | OTel Collector |
|---|---|---|---|
| RAM total (MiB)   | 369 ± 22  | 2171 ± 31 | 90 ± 4  |
| CPU total (mCPU)  | 35 ± 10   | 84 ± 27   | 9 ± 2   |
| Disco PVC tras 24 h (MiB) | - | - | passthrough (0) |
| Query latency p50 (ms) | - | - | - |
| Query latency p95 (ms) | - | - | - |
| Deploy clean → first log (s) | 03:58,87 | 09:09;44 | 8:13,83 |
| Tamaño imagen agente (MiB) | 76.4 | 39.4 | 73.3 |

---

## Detalle por Métrica

### Métrica 1 — Footprint RAM / CPU

```bash
# Comandos utilizados
kubectl top pods -n observability --no-headers | awk '{cpu+=$2; mem+=$3} END {print "Loki stack:", cpu"m CPU,", mem"Mi RAM"}'
kubectl top pods -n elastic --no-headers | awk '{cpu+=$2; mem+=$3} END {print "EFK stack:", cpu"m CPU,", mem"Mi RAM"}'
kubectl top pods -n otel --no-headers | awk '{cpu+=$2; mem+=$3} END {print "OTel stack:", cpu"m CPU,", mem"Mi RAM"}'
```

|Muestra 1|||
|---|---|---|             
| ***Stack*** | ***CPU (mCPU)***| ***RAM (MiB)*** |
| Loki + Promtail + Grafana | 29 | 344 |
| EFK (ES + Fluent Bit + Kibana) | 113 | 2194 |
| OTel Collector | 9 | 92 |

|Muestra 2 (+1h)|||
|---|---|---|             
| ***Stack*** | ***CPU (mCPU)***| ***RAM (MiB)*** |
| Loki + Promtail + Grafana | 30 | 385 |
| EFK (ES + Fluent Bit + Kibana) | 61  | 2182 |
| OTel Collector | 8 | 85 |

|Muestra 3 (+2h)|||
|---|---|---|             
| ***Stack*** | ***CPU (mCPU)***| ***RAM (MiB)*** |
| Loki + Promtail + Grafana | 47 | 378 |
| EFK (ES + Fluent Bit + Kibana) | 78 | 2136 |
| OTel Collector | 11 | 93 |

---

### **Media y desviación estándar**

**Loki + Promtail + Grafana**
* CPU: muestras 29, 30, 47 → media: 35.3m | desv: 9.9m
* RAM: muestras 344, 385, 378 → media: 369 MiB | desv: 22.0 MiB

**EFK (ES + Fluent Bit + Kibana)**
* CPU: muestras 113, 61, 78 → media: 84.0m | desv: 26.6m
* RAM: muestras 2194, 2182, 2136 → media: 2170.7 MiB | desv: 30.5 MiB

**OTel Collector**
* CPU: muestras 9, 8, 11 → media: 9.3m | desv: 1.5m
* RAM: muestras 92, 85, 93 → media: 90.0 MiB | desv: 4.4 MiB

---

### Métrica 2 — Disk Usage del PVC tras 24 hs

```bash
MSYS_NO_PATHCONV=1 kubectl -n observability exec loki-0 -c loki -- du -sh /var/loki
MSYS_NO_PATHCONV=1 kubectl -n elastic exec scraper-es-default-0 -c elasticsearch -- du -sh /usr/share/elasticsearch/data
```

| Stack | Baseline (t=0) | Tras 24 hs |
|---|---|---|
| Loki | 1.5 MiB | - |
| Elasticsearch | 7.0 MiB | - |
| OTel Collector | passthrough (0) | passthrough (0) |

---

### Métrica 3 — Query Latency p50 / p95

**Pregunta canónica:** "errores del scraper en la última hora, agrupados por producto"

```bash
# Loki / LogQL
time curl -sG http://localhost:3100/loki/api/v1/query_range \
  --data-urlencode 'query=sum by (producto) (count_over_time({namespace="ml-scraper", app="scraper"} | json | level="ERROR" [1h]))' \
  --data-urlencode 'start='$(date -u -d '1 hour ago' +%s)000000000 \
  --data-urlencode 'end='$(date -u +%s)000000000 \
  -o /dev/null

# Elasticsearch / KQL
time curl -sX POST "http://localhost:9200/scraper-*/_search" -H 'Content-Type: application/json' -d '{
  "size": 0,
  "query": {"bool": {"must": [
    {"range": {"@timestamp": {"gte": "now-1h"}}},
    {"term": {"level": "ERROR"}}
  ]}},
  "aggs": {"by_producto": {"terms": {"field": "producto.keyword"}}}
}' -o /dev/null
```

| Stack | p50 (ms) | p95 (ms) |
|---|---|---|
| Loki (LogQL) | - | - |
| Elasticsearch (KQL) | - | - |
| OTel → backend | - | - |

---

### Métrica 4 — Tiempo de Deployment desde Cero

Comandos utilizados
```bash
kubectl delete namespace observability efk otel --wait=true
```
```bash
START=$(date +%s); cd observability && ./install.sh; END=$(date +%s)
echo "Loki stack deploy: $((END-START))s"
# Loki stack deploy: 144s
```
```bash
START=$(date +%s); cd efk && ./install.sh; END=$(date +%s)
echo "EFK stack deploy: $((END-START))s"
# EFK stack deploy: 428s
```
```bash
START=$(date +%s); cd otl && ./install.sh; END=$(date +%s)
echo "OTel stack deploy: $((END-START))s"
```

| Stack | Tiempo clean → primer log (s) |
|---|---|
| Loki + Promtail + Grafana | 03:58,87 (**238s**) |
| EFK | 09:09;44 (**549s**) |
| OTel Collector | 8:13,83 (**493s**) |

---

### Métrica 5 — Tamaño de Imagen del Agente

```bash
docker exec k3d-scraper-server-0 crictl images | grep -E "promtail|fluent-bit|opentelemetry-collector"
```

| Agente | Imagen | Tamaño (MiB) |
|---|---|---|
| Promtail | `grafana/promtail:3.0.0` | 76.4 |
| Fluent Bit | `fluent/fluent-bit:3.2.4` | 39.4 |
| OTel Collector | `otel/opentelemetry-collector-contrib:0.110.0` | 73.3 |

---