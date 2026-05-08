# Testing Guide — TP2 Part 1 + Part 2 + Part 3

Guía completa para probar los stacks de observabilidad (Loki, EFK y OpenTelemetry) en un flujo end-to-end.

---

## 0. Preparación del Cluster

Antes de empezar, asegurate de tener un cluster limpio de **k3d**.

### 0.1 Crear Cluster k3d
```bash
k3d cluster create scraper \
  --servers 1 \
  --agents 0 \
  --port "30000-30100:30000-30100@server:0" \
  --port "8080:80@loadbalancer"
```

### 0.2 Exportar variables de entorno
```bash
# Requerido para Grafana (Parte 1)
export GRAFANA_ADMIN_PASSWORD='admin'

# Opcional (si querés probar alertas en Discord)
export DISCORD_WEBHOOK_URL='https://discord.com/api/webhooks/...'
```

---

## 1. Desplegar Stacks Previos (Pre-requisitos para Parte 3)

Para que OpenTelemetry pueda hacer fan-out, los backends deben estar activos.

### 1.1 Parte 1 — Loki + Promtail + Grafana
```bash
cd observability
chmod +x install.sh
./install.sh
cd ..
```

### 1.2 Parte 2 — EFK (Elasticsearch + Kibana + Fluent Bit)
```bash
cd efk
chmod +x install.sh
./install.sh
cd ..
```

---

## 2. Desplegar Parte 3 — OpenTelemetry (HIT #1 + #2)

Esta es la parte central de la sesión actual.

### 2.1 Navegar a la carpeta y ejecutar instalación
```bash
cd otel
chmod +x install.sh
./install.sh
```

El script `otel/install.sh` realizará lo siguiente:
- ✅ Crea namespaces `otel` y `otel-operator-system`.
- ✅ Instala `cert-manager` (requerido por el operador para webhooks TLS).
- ✅ Instala el **OpenTelemetry Operator** vía Helm.
- ✅ Configura el **RBAC** para que el collector tenga acceso a metadata de k8s.
- ✅ Despliega el **OpenTelemetryCollector** como `DaemonSet` (Agente).
- ✅ **Escala a 0** los agentes viejos (`promtail` y `fluent-bit`) para centralizar todo en OTel.

### 2.2 Verificar que el Agente está listo
```bash
kubectl -n otel get pods -l app.kubernetes.io/name=agent-collector
# Esperado: Pod en estado Running
```

---

## 3. Verificación de Hits (End-to-End)

### 3.1 Desplegar Scraper para generar logs
```bash
cd ../TP1/HIT7/k8s
kubectl create namespace ml-scraper
kubectl apply -n ml-scraper -f .
```

### 3.2 Crear Job manual y esperar completitud
```bash
kubectl -n ml-scraper create job --from=cronjob/scraper-hourly scraper-otel-test-1
kubectl -n ml-scraper wait --for=condition=complete job/scraper-otel-test-1 --timeout=600s
```

### 3.3 Validar HIT #2 (Recolección y Enriquecimiento)
Verificamos que el OTel Collector esté capturando los logs y añadiendo la metadata de Kubernetes.

```bash
kubectl -n otel logs ds/agent-collector | Select-String "ml-scraper" -Context 5
```
**Resultado esperado:**
Deberías ver bloques JSON de logs donde aparezcan atributos como:
- `k8s.namespace.name: ml-scraper`
- `k8s.pod.name: scraper-otel-test-1-xxxxx`
- `k8s.job.name: scraper-otel-test-1`
- `Body: ...` (contenido del log del scraper)

---

## Checklist de Validación Parte 3

### HIT #1 (OTel Operator)
- [ ] Operador en `Running` en namespace `otel-operator-system`.
- [ ] CRDs `opentelemetrycollectors` presentes.
- [ ] `cert-manager` pods en `Running`.

### HIT #2 (OTel Collector Agent)
- [ ] DaemonSet `agent-collector` desplegado en namespace `otel`.
- [ ] Pods del collector en `Running`.
- [ ] Logs del collector muestran el procesamiento de archivos en `/var/log/pods/`.
- [ ] Logs enriquecidos con metadata de K8s (vía `k8sattributes` processor).

---

## Troubleshooting

| Problema | Síntoma | Solución |
|----------|---------|----------|
| OTel Operator falla | Error en webhooks / certs | Verificar que `cert-manager` esté Running y que los CRDs de cert-manager se hayan instalado correctamente. |
| Collector no ve logs | Atributos k8s vacíos | Revisar RBAC (`kubectl apply -f otel/manifests/rbac.yaml`). El ServiceAccount debe tener permisos de `list/watch` sobre pods y nodes. |
| CrashLoopBackOff | Error de sintaxis en el YAML | Ver logs: `kubectl -n otel logs ds/agent-collector`. OTel es estricto con la indentación del config. |

---

## Limpieza de Parte 3

```bash
# Desinstalar OTel
helm uninstall otel-operator -n otel-operator-system
kubectl delete ns otel otel-operator-system
```
