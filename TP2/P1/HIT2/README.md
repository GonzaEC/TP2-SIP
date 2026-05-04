# HIT #2 — Recolección de logs del scraper con labels Kubernetes

## Objetivo

Refinar el `scrape_configs` de Promtail para filtrar solo el namespace del scraper (`ml-scraper`) y enriquecer cada línea con labels útiles para queryar después.

## Qué se hizo

### 1. Extra scrape config en Promtail

Se agregó un `extraScrapeConfigs` en `observability/helm/promtail-values.yaml` con:

- **kubernetes_sd_configs**: descubre pods en el namespace `ml-scraper`
- **relabel_configs**:
  - `keep` solo pods con `app=scraper`
  - Mapea labels de Kubernetes a labels de Loki: `namespace`, `pod`, `container`, `app`, `job_name`, `node`
  - Configura `__path__` al log file en el host

### 2. Labels seleccionados

| Label | Origen | Cardinalidad |
|---|---|---|
| `namespace` | `__meta_kubernetes_namespace` | Baja |
| `app` | `__meta_kubernetes_pod_label_app` | Baja |
| `pod` | `__meta_kubernetes_pod_name` | Media |
| `container` | `__meta_kubernetes_pod_container_name` | Baja |
| `job_name` | `__meta_kubernetes_pod_label_job_name` | Media |
| `node` | `__meta_kubernetes_node_name` | Baja |

**Regla**: menos de 10 labels totales, ninguno con cardinalidad alta (no IDs random como label).

### 3. Aplicar cambios

```bash
helm upgrade promtail grafana/promtail \
  --version 6.16.0 \
  --namespace observability \
  --values observability/helm/promtail-values.yaml
```

### 4. Fix del CronJob: labels en el pod template

Los manifests del CronJob y Job en `TP1/HIT7/k8s/` y `TP1/HIT8/k8s/` no incluian labels en el `podTemplate`. Sin `app: scraper`, el `keep` del relabel config filtraba todos los pods generados por el CronJob y los logs nunca llegaban a Loki.

Se agregó `metadata.labels.app: scraper` al `template` de `cronjob.yaml` y `job.yaml`:

```yaml
template:
  metadata:
    labels:
      app: scraper
  spec:
    ...
```

Luego se re-deployó el CronJob:

```bash
kubectl -n ml-scraper replace --force -f TP1/HIT7/k8s/cronjob.yaml
```

### 5. Generar tráfico de prueba

```bash
kubectl -n ml-scraper create job --from=cronjob/scraper-hourly scraper-test-1
kubectl -n ml-scraper wait --for=condition=complete job/scraper-test-1 --timeout=600s
```

## Validación

En Grafana → Explore → Loki:

```
{namespace="ml-scraper", app="scraper"}
```

Y refinada por job:

```
{namespace="ml-scraper", app="scraper", job_name="scraper-test-1"}
```

## Captura de validación

![HIT2 - Labels Kubernetes](/observability/screenshots/hit2-labels.png)
