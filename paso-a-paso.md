# Paso a paso — TP2 HIT1 y HIT2

## Pre-requisitos

- **k3s** (o cualquier cluster Kubernetes local — minikube también sirve)
- **kubectl** configurado apuntando al cluster
- **Helm v3** instalado
- El repo clonado: `git clone <url-del-repo>`

---

## Ejercicio 1 — Deploy del stack Loki + Promtail + Grafana

**1. Posicionarse en la carpeta del repo**
```bash
cd TP2-SIP
```

**2. Definir la password de Grafana** (elegir cualquier password)
```bash
export GRAFANA_ADMIN_PASSWORD='mipassword123'
```

**3. Ejecutar el script de instalación**
```bash
cd observability
./install.sh
```
> El script crea el namespace, el secret de Grafana, y despliega Loki, Promtail y Grafana con Helm.

**4. Verificar que los pods están corriendo**
```bash
kubectl -n observability get pods
```
Deben aparecer 3 pods en estado `Running`:
```
loki-0          1/1   Running
promtail-xxxxx  1/1   Running
grafana-xxxxx   1/1   Running
```

**5. Acceder a Grafana**
```bash
kubectl -n observability port-forward svc/grafana 30000:80
```
Abrir en el navegador: `http://localhost:30000`
Login: `admin` / `mipassword123`

**6. Validar end-to-end**

En Grafana → **Explore** → datasource **Loki** → ejecutar la query:
```
{namespace="observability"}
```
Si aparecen logs del propio stack, el pipeline **Promtail → Loki → Grafana** está funcionando. ✓

---

## Ejercicio 2 — Recolección de logs del scraper con labels Kubernetes

Este ejercicio depende de que el scraper del TP1 esté desplegado en el namespace `ml-scraper`.

**1. Verificar que el namespace del scraper existe**
```bash
kubectl get namespace ml-scraper
```
Si no existe, crearlo:
```bash
kubectl create namespace ml-scraper
```

**2. Actualizar Promtail con la configuración del HIT2**
```bash
helm upgrade promtail grafana/promtail \
  --version 6.16.0 \
  --namespace observability \
  --values observability/helm/promtail-values.yaml
```

**3. Asegurarse de que el CronJob/Job del scraper tiene el label `app: scraper`**

El manifiesto del CronJob debe tener esto en el `template`:
```yaml
template:
  metadata:
    labels:
      app: scraper
```
Si ya está en el repo, aplicarlo:
```bash
kubectl -n ml-scraper replace --force -f TP1/HIT7/k8s/cronjob.yaml
```

**4. Generar tráfico de prueba (disparar el job manualmente)**
```bash
kubectl -n ml-scraper create job --from=cronjob/scraper-hourly scraper-test-1
kubectl -n ml-scraper wait --for=condition=complete job/scraper-test-1 --timeout=600s
```

**5. Validar en Grafana → Explore → Loki**

Query general:
```
{namespace="ml-scraper", app="scraper"}
```

Query filtrada por job específico:
```
{namespace="ml-scraper", app="scraper", job_name="scraper-test-1"}
```

Si aparecen los logs del scraper con esos labels, el HIT2 está funcionando. ✓

---

## Resumen

| Ejercicio | Qué valida |
|---|---|
| HIT1 | Los 3 pods en `observability` están `Running` y Grafana muestra logs de `{namespace="observability"}` |
| HIT2 | Grafana muestra logs del scraper con `{namespace="ml-scraper", app="scraper"}` |
