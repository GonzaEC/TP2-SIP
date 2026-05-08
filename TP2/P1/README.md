# TP 2 · Parte 1 — Logging centralizado con Loki

Stack **Loki** (Loki + Promtail + Grafana) desplegado en el namespace `observability`. Recolecta y estructura los logs del scraper de MercadoLibre corriendo en `ml-scraper`, habilitando queries LogQL, dashboards y alertas via Discord.

## Hits

| Hit | Descripción | Estado |
|-----|-------------|--------|
| [HIT #1](HIT1/README.md) | Deploy del stack Loki + Promtail + Grafana (3 charts Helm) | ✅ |
| [HIT #2](HIT2/README.md) | Promtail scrape config — labels Kubernetes del scraper | ✅ |
| [HIT #3](HIT3/README.md) | Logs JSON estructurados en el scraper (LogstashEncoder / python-json-logger) | ✅ |
| [HIT #4](HIT4/README.md) | Cookbook LogQL: 5 queries operacionales documentadas | ✅ |
| [HIT #5](HIT5/README.md) | Dashboard Grafana provisionado as-code (ConfigMap + JSON) | ✅ |
| [HIT #6](HIT6/README.md) | Alertas via Grafana Alerting → Discord (bonus +5%) | ✅ |

## Levantar el stack

```bash
export GRAFANA_ADMIN_PASSWORD='<tu-password>'
# Opcional — solo para el Hit #6 (bonus):
export DISCORD_WEBHOOK_URL='https://discord.com/api/webhooks/<id>/<token>'

cd observability
chmod +x install.sh
./install.sh
```

Ver [observability/README.md](../../observability/README.md) para detalles completos y verificación de cada hit.

## Namespaces

| Namespace | Stack | Acceso |
|-----------|-------|--------|
| `observability` | Loki + Promtail + Grafana (Parte 1) | `http://<NODE_IP>:30000` |
| `ml-scraper` | Scraper de MercadoLibre (fuente de logs) | — |

## Acceso a Grafana

```bash
NODE_IP=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}')
echo "http://${NODE_IP}:30000"

# Password:
kubectl -n observability get secret grafana-admin -o jsonpath='{.data.admin-password}' | base64 -d
```

## Verificación rápida

```bash
# Pods corriendo
kubectl -n observability get pods

# Pipeline cerrado: logs del scraper en Loki
kubectl -n ml-scraper create job --from=cronjob/scraper-hourly scraper-loki-test-1
# Luego en Grafana → Explore → {namespace="ml-scraper", app="scraper"} | json
```
