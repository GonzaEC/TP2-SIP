# Observability — Loki + Promtail + Grafana

Stack de logging centralizado para el scraper del TP 2 · Parte 1.

## Levantar el stack

```bash
export GRAFANA_ADMIN_PASSWORD='<tu-password-segura>'
cd observability && ./install.sh
```

## Verificar

```bash
kubectl -n observability get pods
kubectl -n observability get svc grafana
```

Abrir `http://<node-ip>:30000` con `admin` / `$GRAFANA_ADMIN_PASSWORD`.

## Estructura

```
observability/
├── helm/
│   ├── loki-values.yaml       # Loki single-binary, filesystem, 7d retention
│   ├── promtail-values.yaml   # DaemonSet, tolerations para k3s
│   └── grafana-values.yaml    # NodePort 30000, datasource Loki provisionado
├── manifests/
│   ├── namespace.yaml
│   ├── grafana-secret.yaml    # Placeholder — NO commitear secrets reales
│   └── grafana-nodeport.yaml
├── dashboards/
│   └── scraper-overview.json  # Hit #5
├── queries/
│   └── logql-cookbook.md      # Hit #4
└── install.sh                 # Script idempotente
```

## Variables de entorno

| Variable | Requerida | Descripción |
|---|---|---|
| `GRAFANA_ADMIN_PASSWORD` | Sí | Password de admin de Grafana |
| `DISCORD_WEBHOOK_URL` | No | Webhook para alertas (Hit #6, bonus) |

## Versiones pinneadas

| Componente | Chart | Versión | App version |
|---|---|---|---|
| Loki | `grafana/loki` | 6.16.0 | Loki 3.x |
| Promtail | `grafana/promtail` | 6.16.0 | Promtail 3.x |
| Grafana | `grafana/grafana` | 8.5.0 | Grafana 11.x |
