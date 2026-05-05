# Testing Guide — P2/HIT1 + HIT2 + Scraper Integration

Guía completa para probar P2/HIT1, P2/HIT2 y el scraper real en un flujo end-to-end.

---

## Pre-requisitos

- **k3d cluster** creado y corriendo (`k3d-mi-cluster` o similar)
- **kubectl** y **helm** configurados
- Docker corriendo en el host

Verificar:
```bash
kubectl get nodes
# NAME                   STATUS   ROLES              AGE
# k3d-mi-cluster-server-0  Ready    control-plane,master  Xm
```

---

## 1. Desplegar Stack EFK (P2/HIT1 + HIT2)

### 1.1 Navegar a la carpeta
```bash
cd efk
chmod +x install.sh
```

### 1.2 Ejecutar instalación
```bash
./install.sh
```

El script automáticamente:
- ✅ Crea namespaces `elastic` y `elastic-system`
- ✅ Instala ECK Operator via Helm
- ✅ Despliega Elasticsearch (single-node, 8.17.3)
- ✅ Despliega Kibana (NodePort 30001)
- ✅ Instala Fluent Bit como DaemonSet
- ✅ Aplica ILM policy e index templates

**Duración esperada**: 3-5 minutos

### 1.3 Verificar que está listo
```bash
kubectl -n elastic get pods
# NAME                                   READY   STATUS    RESTARTS   AGE
# fluent-bit-xxxxx                       1/1     Running   0          2m
# scraper-es-default-0                   1/1     Running   0          3m
# scraper-kb-xxxxxxxx-xxxxx              1/1     Running   0          3m
```

### 1.4 Obtener credenciales
```bash
NODE_IP=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}')
PASSWORD=$(kubectl -n elastic get secret scraper-es-elastic-user -o jsonpath='{.data.elastic}' | base64 -d)

echo "Kibana: https://${NODE_IP}:30001"
echo "Usuario: elastic"
echo "Contraseña: $PASSWORD"
```

### 1.5 Acceder a Kibana
1. Abrir `https://<NODE_IP>:30001`
2. Ingresar `elastic` / `$PASSWORD`
3. Esperar a que cargue completamente (puede tardar 1-2 min)

---

## 2. Desplegar Scraper Real (TP1/HIT7)

### 2.1 Navegar a manifiestos del scraper
```bash
cd ../TP1/HIT7/k8s
```

### 2.2 Crear namespace
```bash
kubectl create namespace ml-scraper
```

### 2.3 Aplicar manifiestos
```bash
kubectl apply -n ml-scraper -f .
```

Esto crea:
- ConfigMap con configuración del scraper
- PersistentVolumeClaim para outputs (1GB)
- Job `scraper-once` (ejecución inmediata)
- CronJob `scraper-hourly` (cada hora)

### 2.4 Esperar a que complete el Job
```bash
kubectl -n ml-scraper wait --for=condition=complete job/scraper-once --timeout=600s
```

Salida esperada:
```
job.batch/scraper-once condition met
```

### 2.5 Verificar que el scraper ejecutó
```bash
kubectl -n ml-scraper logs job/scraper-once | tail -10
```

Debería ver líneas como:
```
[SUCCESS] JSON guardado: /app/output/bicicleta_rodado_29.json
[SUCCESS] JSON guardado: /app/output/iphone_16_pro_max.json
[SUCCESS] JSON guardado: /app/output/geforce_rtx_5090.json
```

---

## 3. Validar P2/HIT2 (Fluent Bit + Elasticsearch)

### 3.1 Crear nuevo job del scraper
```bash
kubectl -n ml-scraper create job --from=cronjob/scraper-hourly scraper-efk-test-1
```

### 3.2 Esperar a que complete
```bash
kubectl -n ml-scraper wait --for=condition=complete job/scraper-efk-test-1 --timeout=600s
```

### 3.3 Verificar que los logs llegaron a Elasticsearch
```bash
# Port-forward a Elasticsearch
kubectl -n elastic port-forward svc/scraper-es-http 9200:9200 &
sleep 3

# Verificar índices
PASSWORD=$(kubectl -n elastic get secret scraper-es-elastic-user -o jsonpath='{.data.elastic}' | base64 -d)
curl -s -u "elastic:$PASSWORD" "http://localhost:9200/_cat/indices?v" | grep scraper

# Output esperado:
# yellow  open  scraper-logs-2026.05.05  ...  N docs
```

### 3.4 En Kibana — Stack Management → Index Management
1. Debería aparecer `scraper-logs-YYYY.MM.DD`
2. Click en el índice → ver detalles

### 3.5 En Kibana — Discover
1. Crear un "Data View" para `scraper-logs-*`
2. Ver los logs parseados con campos:
   - `level`, `producto`, `logger`, `message`
   - `kubernetes.namespace`, `kubernetes.pod_name`, `kubernetes.labels.app`
   - `@timestamp`

---

## Checklist de Validación

### HIT #1 (ECK Operator + Elasticsearch + Kibana)
- [ ] `./install.sh` completa sin errores
- [ ] `kubectl -n elastic get pods` → todos en `Running`
- [ ] Kibana accesible en https://<NODE_IP>:30001
- [ ] Kibana login exitoso con elastic/<password>

### HIT #2 (Fluent Bit DaemonSet)
- [ ] Fluent Bit pod en `Running`
- [ ] Fluent Bit logs sin errores (`kubectl -n elastic logs -l app.kubernetes.io/name=fluent-bit`)
- [ ] Índices `scraper-logs-*` creados en Elasticsearch
- [ ] Kibana Discover muestra logs del scraper con campos parseados

### Scraper Integration
- [ ] TP1/HIT7 manifiestos aplicados en namespace `ml-scraper`
- [ ] `scraper-once` job completó exitosamente
- [ ] `scraper-hourly` cronjob creado
- [ ] Logs del scraper aparecen en Elasticsearch cuando se crea un job
- [ ] En Kibana se ven los logs del scraper en Discover

---

## Troubleshooting

| Problema | Síntoma | Solución |
|----------|---------|----------|
| Kibana no carga | "No se puede cargar la página" | Esperar 1-2 min, recargrar (Ctrl+Shift+R), verificar logs: `kubectl -n elastic logs scraper-kb-*` |
| Fluent Bit no inicia | Pod en `ContainerCreating` | Verificar `/etc/machine-id` existe en nodo k3d, o recrear pod |
| Elasticsearch no responde | `curl` timeouts | Verificar `kubectl -n elastic get pods scraper-es-default-0` está `Running`, esperar 2-3 min |
| Índices no se crean | Elasticsearch tiene 0 docs | Verificar Fluent Bit logs, confirmar que scraper pod tiene label `app=scraper` |
| Scraper fails | Job en estado `Failed` | Ver logs: `kubectl -n ml-scraper logs job/scraper-once` |

---

## Limpieza Completa (si necesitas empezar de nuevo)

```bash
# Eliminar todo de P2
helm uninstall fluent-bit -n elastic 2>/dev/null || true
helm uninstall eck-operator -n elastic-system 2>/dev/null || true
kubectl delete ns elastic elastic-system 2>/dev/null || true

# Eliminar scraper
kubectl delete ns ml-scraper 2>/dev/null || true

# Esperar a que se limpie
sleep 10

# Reiniciar desde el paso 1
```

---

## Archivos Clave

| Ruta | Descripción |
|------|-----------|
| `efk/install.sh` | Script principal que orquesta todo |
| `efk/helm/fluent-bit-values.yaml` | Config de Fluent Bit (Input → Filter → Output) |
| `efk/manifests/elasticsearch.yaml` | Config de Elasticsearch single-node |
| `efk/README.md` | Detalles técnicos de HIT #1 y HIT #2 |
| `TP1/HIT7/k8s/` | Manifiestos del scraper (configmap, pvc, job, cronjob) |
| `TP1/HIT7/README.md` | Detalles de deployment del scraper |

---

## Duración Total

- **Desplegar EFK**: 3-5 min
- **Desplegar Scraper**: 5-10 min (el scraper tarda en scrapear)
- **Verificar en Kibana**: 2-3 min

**Total**: ~15-20 minutos para un flujo completo clean.

