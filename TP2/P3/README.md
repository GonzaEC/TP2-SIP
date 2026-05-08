# TP 2 · Parte 3 — OpenTelemetry

Este módulo centraliza la recolección de telemetría (logs, traces) utilizando **OpenTelemetry**.

## Contenido

- [HIT 1: Deploy del OpenTelemetry Operator](./HIT1/README.md)
- [HIT 2: OpenTelemetryCollector en modo Agent (DaemonSet)](./HIT2/README.md)
- HIT 3: Fan-out simultáneo a Loki + Elasticsearch (Pendiente)
- HIT 4: Reemplazo de agentes legacy (Pendiente)
- HIT 5: Instrumentación del scraper con SDK (Pendiente)
- HIT 6: Bonus: Traces (Pendiente)

## Estructura de archivos técnicos

Los archivos de implementación se encuentran en la raíz del repositorio bajo la carpeta `otel/`:

```
otel/
├── README.md
├── install.sh
├── helm/
│   └── otel-operator-values.yaml
└── manifests/
    ├── namespace.yaml
    ├── rbac.yaml
    └── collector-agent.yaml
```
