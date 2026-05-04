# HIT #1 — Deploy del stack Loki + Promtail + Grafana

## Objetivo

Desplegar los 3 charts separados de Grafana Helm en un namespace dedicado (`observability`). El objetivo es **dejar el stack arriba y conectado**, sin todavía tocar el scraper.

## Qué se hizo

### 1. Namespace y repo Helm

```bash
kubectl create namespace observability
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update
```

### 2. Loki en modo single-binary con storage local

Se eligió **single-binary** porque es un cluster local k3s. Los modos simple-scalable y microservices solo tienen sentido con S3/GCS y volumen alto.

**Archivo**: `observability/helm/loki-values.yaml`

Configuración clave:
- `deploymentMode: SingleBinary` — todo Loki en un solo pod
- `storage.type: filesystem` — sin cloud, storage local
- `retention_period: 168h` — 7 días de retención
- `storageClass: local-path` — viene en k3s out-of-the-box
- Resources limitados: 256Mi-512Mi RAM, 100m-500m CPU

### 3. Promtail como DaemonSet

**Archivo**: `observability/helm/promtail-values.yaml`

- Apunta a `http://loki.observability.svc.cluster.local:3100/loki/api/v1/push`
- Resources: 64Mi-128Mi RAM, 50m-200m CPU
- Tolera taints `NoSchedule` (necesario en k3s single-node)

### 4. Grafana con datasource Loki provisionado

**Archivo**: `observability/helm/grafana-values.yaml`

- Admin credentials via `existingSecret: grafana-admin` (NO hardcodeado)
- `service.type: NodePort` en puerto `30000`
- Datasource Loki provisionado as-code (sin clicks en la UI)
- Dashboard provider configurado para Hit #5

## Cómo levantar

```bash
export GRAFANA_ADMIN_PASSWORD='<tu-password>'
cd observability && ./install.sh
```

## Verificación

```bash
# Pods corriendo
kubectl -n observability get pods

# Output esperado:
# NAME                      READY   STATUS    RESTARTS   AGE
# loki-0                    1/1     Running   0          3m
# promtail-xxxxx            1/1     Running   0          3m
# grafana-xxxxx             1/1     Running   0          2m

# Services
kubectl -n observability get svc

# Output esperado:
# NAME       TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)
# grafana    NodePort    10.43.x.x       <none>        80:30000/TCP
# loki       ClusterIP   10.43.x.x       <none>        3100/TCP,9095/TCP

# PVCs Bound
kubectl -n observability get pvc
```

## Acceso a Grafana

```bash
kubectl -n observability port-forward svc/grafana 30000:80
```

Abrir `http://localhost:30000` → login con `admin` / `$GRAFANA_ADMIN_PASSWORD`

## Validación end-to-end

En Grafana → **Explore** → datasource **Loki** → query:

```
{namespace="observability"}
```

Deben aparecer logs del propio stack. Eso prueba que el pipeline **Promtail → Loki → Grafana** está cerrado end-to-end.

## Versiones pinneadas

| Componente | Chart | Versión | App version |
|---|---|---|---|
| Loki | `grafana/loki` | 6.16.0 | Loki 3.1.1 |
| Promtail | `grafana/promtail` | 6.16.0 | Promtail 3.0.0 |
| Grafana | `grafana/grafana` | 8.5.0 | Grafana 11.x |

## Archivos relevantes

| Archivo | Descripción |
|---|---|
| `observability/helm/loki-values.yaml` | Config de Loki single-binary |
| `observability/helm/promtail-values.yaml` | Config de Promtail DaemonSet |
| `observability/helm/grafana-values.yaml` | Config de Grafana + datasource |
| `observability/manifests/grafana-secret.yaml` | Placeholder del secret |
| `observability/install.sh` | Script idempotente |

## Captura de validación

![HIT1 - Grafana Explore](../../screenshots/hit1-stack.png)
