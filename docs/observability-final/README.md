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

- **Cluster local:** k3d sobre una VM de desarrollo.
- **Carga sintética:** log generator que emite ~300 MB/día distribuidos en 4 servicios ficticios (`scraper`, `normalizer`, `api-gateway`, `scheduler`), equivalente al volumen de producción actual
- **Duración de cada experimento:** 24 horas de ingesta continua antes de tomar mediciones de disco.

### RAM y CPU — `kubectl top`

Cada stack fue desplegado en un namespace aislado. Las mediciones se tomaron con:

```bash
# Esperar steady state
kubectl top pods -n <namespace> --sort-by=memory
```

Las capturas correspondientes están en `screenshots/kubectl-top-*.png`.

Para reproducir desde cero:

```bash
# Loki stack
kubectl apply -f deploy/loki-stack.yaml
kubectl wait --for=condition=ready pod -l app=loki -n loki --timeout=300s

kubectl top pods -n loki

# EFK stack
kubectl apply -f deploy/efk-stack.yaml
kubectl wait --for=condition=ready pod -l app=elasticsearch -n efk --timeout=600s

kubectl top pods -n efk

# OTel Collector
kubectl apply -f deploy/otel-collector.yaml
kubectl wait --for=condition=ready pod -l app=otel-collector -n otel --timeout=120s

kubectl top pods -n otel
```

### Tiempo de deploy clean → first log

Medido con `time` desde `kubectl apply` hasta que el primer log aparece en la UI (Grafana/Kibana) o en el backend:

```bash
time kubectl apply -f deploy/<stack>.yaml && \
  kubectl wait --for=condition=ready pod -l app=<app> -n <ns> --timeout=600s
```

El "first log" se verificó visualmente en cada UI y se registró manualmente. Ver valores en `measurements.md`.

### Disco PVC tras 24 h

```bash
# Dentro del pod de cada backend
kubectl exec -n <namespace> <pod-name> -- df -h /data
```

Captura en `screenshots/pvc-disk-usage.png`.

> **Nota:** Los valores de disco PVC para Loki y EFK están pendientes de medición (marcados como N/M en `measurements.md`).

### Query latency p50 / p95

Para Loki, latencia medida con LogQL desde Grafana Explore:

```logql
{service="api-gateway"} |= "error"
```

Se registró el tiempo de respuesta reportado por Grafana para un rango de 1 hora de logs. Ver procedimiento detallado en `measurements.md`.

> **Nota:** Los valores de query latency están pendientes de medición (marcados como N/M en `measurements.md`).

Capturas en `screenshots/query-latency-comparison.png`.

---

## Cambios al scraper (Partes 1–3)

No se realizaron cambios al código del scraper en esta entrega. Todos los archivos modificados en `Parte 4` son exclusivamente documentación bajo `docs/`.

> Si durante la preparación de esta entrega se detectara un bug heredado de Partes 1–3 que requiriera corrección, se documentaría aquí con el commit SHA correspondiente y la justificación. Al momento de esta entrega, no aplica.



## Referencias cruzadas

- ADR magisterial completo: [`../adr/0012-stack-de-observabilidad-final.md`](../adr/0012-stack-de-observabilidad-final.md)
- Mediciones raw: [`measurements.md`](measurements.md)
- Matriz de decisión: [`decision-matrix.md`](decision-matrix.md)
- Ensayo vendor lock-in: [`vendor-lockin-essay.md`](vendor-lockin-essay.md)