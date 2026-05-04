# TP1 SIP — HIT 7: Orquestación con Kubernetes (k3s)

Despliegue del scraper del [HIT 6](../HIT6/README.md) en un cluster Kubernetes (k3s/k3d) usando recursos nativos de batch: Job, CronJob, ConfigMap y PersistentVolumeClaim.

La imagen Docker está publicada públicamente en GitHub Container Registry:
```
ghcr.io/gonzaec/ml-scraper:latest
```
El cluster la descarga automáticamente — no hace falta construirla ni importarla manualmente.

---

## Manifiestos (`HIT7/k8s/`)

| Archivo | Qué hace |
|---|---|
| `configmap.yaml` | Variables de entorno: `BROWSER`, `HEADLESS`, `LOG_LEVEL`, lista de `PRODUCTS` |
| `pvc.yaml` | Solicita 1 GB de almacenamiento persistente (`storageClassName: local-path`) |
| `job.yaml` | Ejecuta el scraper **una vez** y guarda resultados en el PVC |
| `cronjob.yaml` | Ejecuta el scraper **cada hora** (`0 * * * *`) con histórico de 3 ejecuciones exitosas |

---

## Pre-requisitos

- **Docker Desktop** instalado y corriendo.
- **k3d** instalado (`winget install k3d` en Windows, o descarga desde [github.com/k3d-io/k3d](https://github.com/k3d-io/k3d/releases)).
- **kubectl** instalado (`winget  install kubectl` o `choco install kubernetes-cli`).

---

## Setup del cluster (una sola vez)

```bash
# Crear cluster k3d
k3d cluster create scraper

# Verificar que está funcionando
kubectl get nodes
# NAME                   STATUS   ROLES                  AGE
# k3d-scraper-server-0   Ready    control-plane,master   10s
```

> **Windows:** si `kubectl get nodes` devuelve error de conexión, ejecutá:
> ```powershell
> kubectl config set-cluster k3d-scraper --server=https://127.0.0.1:<PUERTO>
> ```
> El puerto lo ves en la salida de `k3d cluster create scraper` o con `docker ps`.

---

## Despliegue

```bash
# Aplicar todos los manifiestos (desde la raíz del repo)
kubectl apply -f HIT7/k8s/

# Verificar que se crearon
kubectl get configmap scraper-config
kubectl get pvc scraper-output
kubectl get jobs
kubectl get cronjobs
```

---

## Verificar ejecución del Job

```bash
# Ver estado del pod
kubectl get pods -l job-name=scraper-once

# Seguir los logs en tiempo real
kubectl logs -l job-name=scraper-once -f
```

Salida esperada:
```
[PROCESS] Iniciando: bicicleta rodado 29
[SUCCESS] JSON: /app/output/bicicleta_rodado_29.json
[PROCESS] Iniciando: iPhone 16 Pro Max
[SUCCESS] JSON: /app/output/iphone_16_pro_max.json
[PROCESS] Iniciando: GeForce RTX 5090
[SUCCESS] JSON: /app/output/geforce_rtx_5090.json
```

---

## Verificar el CronJob

```bash
kubectl get cronjob scraper-hourly
# NAME              SCHEDULE    SUSPEND   ACTIVE   LAST SCHEDULE
# scraper-hourly    0 * * * *   False     0        <none>

# Monitorear jobs creados por el cron (aparecen cada hora)
kubectl get jobs --watch
```

---

## Personalizar configuración

Para cambiar el browser, los productos u otras variables, editá `HIT7/k8s/configmap.yaml` y reaplicá:

```bash
kubectl apply -f HIT7/k8s/configmap.yaml
```

---

## Limpieza

```bash
# Borrar todos los recursos del cluster (no borra el cluster en sí)
kubectl delete -f HIT7/k8s/

# Borrar el cluster completo
k3d cluster delete scraper
```

---

## Solución de problemas

| Síntoma | Causa probable | Solución |
|---|---|---|
| `ImagePullBackOff` | La imagen no es pública o la URL está mal | Verificar que `ghcr.io/gonzaec/ml-scraper` sea público en GitHub Packages |
| `kubectl get nodes` da error de conexión | Hostname `host.docker.internal` no resuelve | `kubectl config set-cluster k3d-scraper --server=https://127.0.0.1:<PUERTO>` |
| Job en estado `Failed` | Mercado Libre bloqueó el scraping desde esa IP | Revisar logs con `kubectl logs -l job-name=scraper-once`; es esperado desde IPs de datacenter |
| PVC en `Pending` | Falta el StorageClass `local-path` | Solo ocurre fuera de k3s/k3d; en k3d viene preinstalado |

---

## Notas de diseño

- `imagePullPolicy: Always` garantiza que k8s use siempre la imagen más reciente del registry.
- El PVC es compartido entre Job y CronJob: los outputs se acumulan en el mismo volumen.
- El CI publica automáticamente una nueva versión de la imagen en `ghcr.io` en cada push a `main`.
