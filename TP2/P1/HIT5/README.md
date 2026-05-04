# HIT #5 — Dashboard Grafana provisionado as-code

## Objetivo

Construir un dashboard único en Grafana que muestre métricas del scraper y provisionarlo as-code via ConfigMap.

## Qué se hizo

### 1. Dashboard JSON

Se creó `observability/dashboards/scraper-overview.json` con:

- **Stat panels (parte de arriba)**: total de scrapes hoy, % de éxito, productos con más errores en 24h
- **Time series (parte media)**: queries Q2 y Q3 del Hit #4
- **Table (parte de abajo)**: última corrida exitosa por producto (Q5) + top errores (Q1)

### 2. Provisioning

**ConfigMap**:
```bash
kubectl -n observability create configmap scraper-overview-dashboard \
  --from-file=scraper-overview.json=observability/dashboards/scraper-overview.json
```

**grafana-values.yaml** — extraConfigmapMounts:
```yaml
extraConfigmapMounts:
  - name: scraper-overview-dashboard
    configMap: scraper-overview-dashboard
    mountPath: /var/lib/grafana/dashboards/sip2026
    readOnly: true
```

**dashboardProviders** — ya configurado en el Hit #1:
```yaml
dashboardProviders:
  dashboardproviders.yaml:
    apiVersion: 1
    providers:
      - name: 'sip2026'
        orgId: 1
        folder: 'SIP 2026'
        type: file
        options:
          path: /var/lib/grafana/dashboards/sip2026
```

### 3. Validación

En Grafana → Dashboards → carpeta **SIP 2026** → aparece **Scraper Overview**.

- Los stat panels muestran números reales (no "No data")
- Los time-series muestran las últimas 6 horas de actividad
- La tabla muestra los productos con timestamps recientes

## Captura de validación

![HIT5 - Dashboard](../../screenshots/hit5-dashboard.png)
