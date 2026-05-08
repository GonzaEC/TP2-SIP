# HIT 5 & HIT 6 — Instrumentación OTel (Java / Spring Boot)

## Qué cambia respecto al HIT 8

| Archivo | Cambio |
|---|---|
| `pom.xml` | +4 dependencias OTel (api, sdk, exporter-otlp, logback-appender) |
| `src/main/resources/logback.xml` | Appender `CONSOLE` reconfigurado + nuevo appender `OTEL` |
| `src/main/java/.../OtelSetup.java` | Nuevo — inicializa SDK, registra shutdown hook |
| `src/main/java/.../MercadoLibreScraper.java` | `OtelSetup.init()` al inicio de `main()` + spans por producto (HIT 6) |
| `otel/manifests/scraper-otlp-config.yaml` | Agregar `OTEL_EXPORTER_OTLP_PROTOCOL: "grpc"` al ConfigMap existente |
| `k8s/cronjob.yaml` | `envFrom` apunta a `scraper-otlp-config` (el ConfigMap ya existente) |
| `k8s/job.yaml` | Igual que CronJob |

**Los `LOG.info/warn/error` en el resto del código NO se tocan.**
El bridge Logback→OTel los captura automáticamente.

---

## Cambios en arquitectura

```
TP1/HIT8/
├── pom.xml                          ← reemplazo
├── k8s/
│   ├── cronjob.yaml                 ← reemplazo
│   └── job.yaml                     ← reemplazo
└── src/main/
    ├── resources/
    │   └── logback.xml              ← reemplazo
    └── java/ar/edu/sip/
        ├── OtelSetup.java           ← nuevo
        └── MercadoLibreScraper.java ← reemplazo

otel/manifests/
└── scraper-otlp-config.yaml         ← agregar una línea (ver abajo)
```

---

## Pasos de despliegue

### 1. Actualizar scraper-otlp-config.yaml

Se modifico el ConfigMap que ya esta en `otel/manifests/scraper-otlp-config.yaml` con una línea nueva, especificando el tipo de protocolo que el exportador utilizara: `OTEL_EXPORTER_OTLP_PROTOCOL: "grpc"`.

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: scraper-otlp-config
  namespace: ml-scraper
data:
  OTEL_EXPORTER_OTLP_ENDPOINT: "http://agent-collector.otel.svc.cluster.local:4317"
  OTEL_EXPORTER_OTLP_PROTOCOL: "grpc"
  OTEL_SERVICE_NAME: "scraper"
  OTEL_LOGS_EXPORTER: "otlp"
  OTEL_TRACES_EXPORTER: "otlp"
```

### 2. Build de la imagen

```bash
# Desde TP1/HIT8/
mvn package -DskipTests
docker build -t ml-scraper:otel-v1 .
```

Si el cluster no tiene acceso a registry externo (caso habitual en k3d):
```bash
k3d image import ml-scraper:otel-v1 -c <nombre-del-cluster>
```

### 3. Aplicar manifests

```bash
kubectl apply -f otel/manifests/scraper-otlp-config.yaml
kubectl apply -f TP1/HIT8/k8s/cronjob.yaml
kubectl apply -f TP1/HIT8/k8s/job.yaml
```

### 4. Correr el Job de prueba

```bash
kubectl -n ml-scraper create job scraper-sdk-test-1 --from=cronjob/scraper-hourly
kubectl -n ml-scraper wait --for=condition=complete job/scraper-sdk-test-1 --timeout=600s
```

### 5. Verificar en Loki

Query LogQL para confirmar que el SDK está exportando con trace_id poblado:
```logql
{service_name="scraper"} | json | trace_id != ""
```

Si `trace_id` está poblado → HIT 5 ✅ y HIT 6 ✅

---

## Qué hace cada pieza (resumen conceptual)

**`OtelSetup.java`**
Arranca el SDK de OTel. Crea dos "providers": uno para logs y uno para traces. Ambos apuntan al mismo collector vía gRPC. Registra un shutdown hook para vaciar el buffer antes de que el JVM muera — sin esto, un Job que dura 30 segundos puede perder los últimos logs.

**`logback.xml`**
Tiene dos appenders activos en paralelo. `CONSOLE` emite JSON a stdout usando `LogstashEncoder` con los campos renombrados para que Loki/Kibana los entiendan (`timestamp`, `level`, `message`, etc.) y los campos MDC (`producto`, `browser`, `intento`) como campos de primer nivel. `OTEL` intercepta los mismos eventos y los reenvía al SDK inicializado en `OtelSetup` — sin este appender el SDK nunca recibe los logs.

**`scraper-otlp-config.yaml`**
ConfigMap que le dice al scraper dónde está el collector. Usa el nombre DNS del Service que el OTel Operator crea automáticamente para el DaemonSet (`agent-collector.otel.svc.cluster.local`). Es más estable que una IP de nodo, y en k3d/k3s single-node apunta al mismo lugar de todas formas.

**Spans (HIT 6)**
Cada producto scrapeado queda envuelto en un span `scrape.producto`. Los logs emitidos dentro del span heredan automáticamente su `trace_id` y `span_id`. Esto permite buscar en Loki todos los logs de un scrape específico por `trace_id` en lugar de por rango de tiempo.

---

## Pitfall conocido

El `BatchLogRecordProcessor` agrupa logs antes de exportarlos. Si el scraper termina muy rápido, puede salir antes de que el batch se envíe. El shutdown hook en `OtelSetup.java` resuelve esto esperando hasta 5 segundos para que el buffer se vacíe.

---

## Operación: levantar, depurar y limpiar sin el stack de observabilidad

### Levantar el cluster y verificar estado general

```bash
# Ver si el cluster está corriendo
k3d cluster list

# Si no está corriendo, levantarlo (reemplazar con el nombre real del cluster)
k3d cluster start <nombre-del-cluster>

# Ver todos los pods de los namespaces relevantes
kubectl get pods -n ml-scraper
kubectl get pods -n otel
```

### Deshabilitar el pull remoto de imágenes

Revisar que este presente `imagePullPolicy: IfNotPresent` en los yamls, que hace que k8s use la imagen local si existe. Para forzar que nunca intente ir a un registry remoto, importar la imagen al cluster antes de aplicar:

```bash
k3d image import ml-scraper:otel-v1 -c <nombre-del-cluster>
```

Si ya está importada y el pod sigue intentando hacer pull, verificar que el tag en el yaml coincida exactamente con el tag importado:

```bash
# Ver imágenes disponibles en el nodo del cluster
docker exec k3d-<nombre-del-cluster>-server-0 crictl images
```

### Limpiar PVCs viejos

Los PVCs retienen datos entre reinicios de pods. Si el scraper falla por datos corruptos o configuración vieja, eliminarlos y recrearlos:

```bash
# Ver PVCs existentes
kubectl get pvc -n ml-scraper

# Eliminar (el pod que los usa debe estar terminado primero)
kubectl delete pvc scraper-output -n ml-scraper
kubectl delete pvc postgres-data -n ml-scraper

# Recrear desde los manifests originales
kubectl apply -f TP1/HIT8/k8s/pvc.yaml
kubectl apply -f TP1/HIT8/k8s/postgres-pvc.yaml
```

> ⚠️ Eliminar `postgres-data` borra todos los datos de PostgreSQL. Solo hacerlo si el objetivo es empezar desde cero.

### Leer logs de cada componente

**Logs del scraper (Job en curso o terminado):**
```bash
# Ver el pod que generó el job (puede estar en estado Completed)
kubectl get pods -n ml-scraper

# Leer sus logs (reemplazar con el nombre real del pod)
kubectl logs -n ml-scraper <nombre-del-pod>

# Si el pod ya terminó y los logs se perdieron, buscar en pods anteriores
kubectl logs -n ml-scraper <nombre-del-pod> --previous
```

**Logs del OTel Collector DaemonSet (el que recibe los logs del scraper):**
```bash
# Ver el pod del collector en el namespace otel
kubectl get pods -n otel

# Leer sus logs — aquí se ve si está recibiendo datos por OTLP o filelog
kubectl logs -n otel <pod-del-collector>

# Filtrar solo errores de exportación
kubectl logs -n otel <pod-del-collector> | grep -i "error\|failed\|refused"
```

**Logs de PostgreSQL:**
```bash
kubectl logs -n ml-scraper -l app=postgres
```

### Verificar que el collector está recibiendo datos del scraper

El collector tiene `verbosity: basic` en el exporter `debug`. Con eso imprime una línea por batch recibido. Buscarlo en sus logs:

```bash
kubectl logs -n otel <pod-del-collector> | grep -i "scraper\|otlp\|batch"
```

Si no aparece nada relacionado con el scraper, el problema está en la conexión entre el scraper y el collector. Verificar que el Service existe:

```bash
kubectl get svc -n otel
# Debe aparecer algo como: agent-collector   ClusterIP   ...   4317/TCP
```

### Ejecutar un Job de prueba rápido y seguir sus logs en tiempo real

```bash
# Lanzar el job
kubectl -n ml-scraper create job scraper-debug-1 --from=cronjob/scraper-hourly

# Seguir los logs mientras corre (el pod tarda unos segundos en aparecer)
kubectl get pods -n ml-scraper -w   # esperar que aparezca el pod del job

# Luego en otra terminal:
kubectl logs -n ml-scraper -l job-name=scraper-debug-1 -f
```

### Describir un pod que falló

Cuando un pod queda en `CrashLoopBackOff`, `Error` o `ImagePullBackOff`:

```bash
kubectl describe pod -n ml-scraper <nombre-del-pod>
```

La sección `Events` al final del output es la más útil — muestra exactamente por qué falló (imagen no encontrada, OOMKilled, error de configuración, etc.).