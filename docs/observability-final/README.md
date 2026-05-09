# Observability Final — Parte 4

Índice de entregables y guía de reproducción de mediciones para la evaluación final de observabilidad.

---

## Estructura del directorio

```
docs/
├── adr/
│   └── 0012-stack-de-observabilidad-final.md   ← ADR magisterial (Hit #3)
└── observability-final/
    ├── README.md                               ← este archivo
    ├── measurements.md                         ← tabla de métricas + comandos (Hit #1)
    ├── decision-matrix.md                      ← matriz de decisión (Hit #2)
    ├── vendor-lockin-essay.md                  ← reflexión vendor lock-in (Hit #4)
    └── screenshots/
        ├── kubectl-top-loki.png                ← RAM/CPU del stack Loki + Promtail + Grafana
        ├── kubectl-top-efk.png                 ← RAM/CPU del stack EFK
        ├── kubectl-top-otel.png                ← RAM/CPU del OTel Collector
        ├── pvc-disk-usage.png                  ← uso de disco PVC tras 24 h de ingesta
        └── query-latency-comparison.png        ← latencia p50/p95 comparada entre stacks
```

---

## Entregables

| Archivo | Descripción | Hit |
|---|---|---|
| `adr/0012-stack-de-observabilidad-final.md` | Decisión arquitectural final: OTel Collector + Loki + Grafana | #3 |
| `measurements.md` | Mediciones empíricas de RAM, CPU, disco y latencia por stack | #1 |
| `decision-matrix.md` | Matriz de decisión multicriterio con ponderación explícita | #2 |
| `vendor-lockin-essay.md` | Reflexión sobre vendor lock-in y riesgos de cada alternativa | #4 |

---

## Cómo se generaron las mediciones

### Entorno

- **Cluster local:** k3d (`scraper`) — 1 server, 0 agents, corriendo sobre Docker Desktop en Windows
- **Carga real:** CronJob `scraper-hourly` del proyecto `ml-scraper`, emitiendo logs reales cada hora durante 24 hs
- **Duración:** 24 horas de ingesta continua antes de tomar mediciones de disco; RAM/CPU medidos en 3 muestras espaciadas 1 hora en steady state

### RAM y CPU — `kubectl top`

Los 3 stacks corrieron simultáneamente en namespaces aislados (`observability`, `elastic`, `otel`). Se tomaron 3 muestras espaciadas 1 hora y se reporta media ± desviación estándar.

```bash
kubectl top pods -n observability --no-headers | awk '{cpu+=$2; mem+=$3} END {print "Loki stack:", cpu"m CPU,", mem"Mi RAM"}'
kubectl top pods -n elastic --no-headers | awk '{cpu+=$2; mem+=$3} END {print "EFK stack:", cpu"m CPU,", mem"Mi RAM"}'
kubectl top pods -n otel --no-headers | awk '{cpu+=$2; mem+=$3} END {print "OTel stack:", cpu"m CPU,", mem"Mi RAM"}'
```

Las capturas del output crudo están en `screenshots/kubectl-top-*.png`.

### Tiempo de deploy clean → first log

Medido con cronómetro en mano desde `k3d cluster create` hasta que el primer log del scraper apareció en Grafana (Loki) o Kibana (EFK). Una sola corrida por stack en cluster limpio (`k3d-test-deploy`).

- **Loki:** cluster limpio → `bash install.sh` → port-forward → scraper job → primer log en Grafana
- **EFK:** cluster limpio → `bash install.sh` → port-forward → scraper job → primer log en Kibana  
- **OTel:** requiere Loki + EFK activos como backends → `bash install.sh` → scraper job → primer log en Grafana

Ver valores exactos en `measurements.md`.

### Disco PVC tras 24 h

Medido con `du -sh` dentro de cada pod backend. En Windows/Git Bash se requiere `MSYS_NO_PATHCONV=1` para evitar conversión de paths:

```bash
MSYS_NO_PATHCONV=1 kubectl -n observability exec loki-0 -c loki -- du -sh /var/loki
MSYS_NO_PATHCONV=1 kubectl -n elastic exec scraper-es-default-0 -c elasticsearch -- du -sh /usr/share/elasticsearch/data
```

OTel Collector no tiene PVC propio — opera como pipeline y hace fan-out a Loki y Elasticsearch sin retención local. Captura en `screenshots/pvc-disk-usage.png`.

### Query latency p50 / p95

Pregunta canónica: *"errores del scraper en la última hora, agrupados por producto"*. Se corrió 10 veces cada query con `time curl` y se reporta p50 (mediana) y p95 (valor 10 ordenado).

```bash
# Loki — requiere port-forward svc/loki 3100:3100
# Elasticsearch — requiere port-forward svc/scraper-es-http 9200:9200
```

Ver comandos completos y tiempos raw en `measurements.md`. Captura en `screenshots/query-latency-comparison.png`.

### Tamaño de imagen del agente

Las imágenes fueron inspeccionadas directamente dentro del nodo k3d, ya que no están disponibles en el Docker local del host:

```bash
docker exec k3d-scraper-server-0 crictl images | grep -E "promtail|fluent-bit|opentelemetry-collector"
```

---

## Cambios al scraper (Partes 1–3)

No se realizaron cambios al código del scraper en esta entrega. Todos los archivos modificados en Parte 4 son exclusivamente documentación bajo `docs/`.

---

## Referencias cruzadas

- ADR magisterial completo: [`../adr/0012-stack-de-observabilidad-final.md`](../adr/0012-stack-de-observabilidad-final.md)
- Mediciones raw: [`measurements.md`](measurements.md)
- Matriz de decisión: [`decision-matrix.md`](decision-matrix.md)
- Ensayo vendor lock-in: [`vendor-lockin-essay.md`](vendor-lockin-essay.md)