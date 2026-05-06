# HIT #5 — Dashboard Kibana provisionado as-code (NDJSON + import API)

## Objetivo

Exportar el dashboard de monitoreo del scraper como **NDJSON** (Newline-Delimited JSON) y automatizar su importación en `install.sh` mediante la **Saved Objects API** de Kibana, logrando provisioning as-code equivalente al ConfigMap de Grafana de la Parte 1.

## Diferencia con Grafana (Parte 1)

| | Grafana (Parte 1) | Kibana (Parte 2) |
|---|---|---|
| Formato | JSON plano | NDJSON (un objeto por línea) |
| Mecanismo | ConfigMap montado en `/var/lib/grafana/dashboards/` | `POST /api/saved_objects/_import` |
| Idempotencia | Grafana lo detecta en el arranque | Parámetro `?overwrite=true` en la API |
| Dependencias | Solo el dashboard JSON | Dashboard + visualizaciones + data view en el mismo NDJSON |

## Qué se hizo

### 1. Dashboard construido en Kibana UI

**Archivo**: `efk/dashboards/scraper-overview.ndjson`

El NDJSON contiene **8 objetos saved** en este orden:

| Línea | Tipo | ID | Descripción |
|-------|------|----|-------------|
| 1 | `index-pattern` | `scraper-logs-dataview` | Data View `scraper-logs` (patrón `scraper-logs-*`) |
| 2 | `visualization` | `vis-total-events` | Metric — Total de eventos hoy |
| 3 | `visualization` | `vis-error-pct` | Metric — % de eventos ERROR vs total |
| 4 | `visualization` | `vis-top5-errors` | Bar chart — Top 5 productos con más errores |
| 5 | `visualization` | `vis-level-pie` | Pie chart — Distribución por level |
| 6 | `visualization` | `vis-events-timeline` | Line chart — Eventos por minuto last 6h |
| 7 | `visualization` | `vis-last-success` | Table — Última corrida exitosa por producto |
| 8 | `dashboard` | `dashboard-scraper-overview` | Dashboard "Scraper Overview" referenciando los 6 paneles |

> El Data View está incluido en el NDJSON — esto lo hace **autosuficiente**: un solo import en un cluster limpio crea todo (data view + visualizaciones + dashboard).

### 2. Import automático en install.sh

```bash
# Port-forward a Kibana
kubectl -n elastic port-forward svc/scraper-kb-http 5601:5601 &
sleep 5

# Import — el header kbn-xsrf: true es OBLIGATORIO
curl -sk -u "elastic:$PASSWORD" \
  -X POST "https://localhost:5601/api/saved_objects/_import?overwrite=true" \
  -H "kbn-xsrf: true" \
  -F file=@efk/dashboards/scraper-overview.ndjson
# Respuesta esperada: {"success":true,"successCount":8}
```

> **Por qué `kbn-xsrf: true`**: Kibana exige un token CSRF en todos los `POST`/`PUT`/`DELETE` como protección contra Cross-Site Request Forgery. Sin ese header el request falla con **HTTP 400** aunque la autenticación sea válida — es el pitfall #1 de todos los que usan la API de Kibana por primera vez.

## Paneles del dashboard

| Panel | Tipo | Query KQL base |
|-------|------|---------------|
| Total eventos hoy | Metric | *(todos)* |
| % Errores | Metric | `level: "ERROR"` |
| Top 5 productos con errores | Bar chart vertical | `level: "ERROR" and producto: *` |
| Distribución por level | Pie chart | *(todos)* |
| Eventos por minuto (last 6h) | Line chart | *(todos)*, breakdown por `level.keyword` |
| Última corrida exitosa por producto | Table | `event: "scrape_completado" and level: "INFO" and items_found >= 1` |

## Verificación

```bash
# Confirmar que el dashboard existe en Kibana via API
curl -sk -u "elastic:$PASSWORD" \
  "https://localhost:5601/api/saved_objects/dashboard/dashboard-scraper-overview" \
  -H "kbn-xsrf: true" | jq '.attributes.title'
# "Scraper Overview"
```

En Kibana → **Dashboards** → debe aparecer **"Scraper Overview"** con datos reales después de correr al menos un job del scraper.

## Archivos relevantes

| Archivo | Descripción |
|---|---|
| `efk/dashboards/scraper-overview.ndjson` | 8 saved objects: data view + 6 visualizaciones + dashboard |
| `efk/install.sh` | Import automático via Saved Objects API (sección Hit #5) |

## Captura de validación

![HIT5 - Dashboard Scraper Overview](/efk/screenshots/hit5-dashboard.png)
