# Stack EFK (Elasticsearch + Fluent Bit + Kibana)

Este directorio contiene la infraestructura para el stack de logging EFK del scraper, coexistiendo con el stack Loki de la Parte 1.

## Estructura

- `helm/`: Configuración de charts (ECK Operator, Fluent Bit).
- `manifests/`: Recursos Kubernetes (Elasticsearch, Kibana, ILM policy).
- `dashboards/`: Dashboards de Kibana (NDJSON).
- `queries/`: Cookbook de queries KQL.
- `install.sh`: Script de instalación idempotente.

## Pre-requisitos

- Cluster k3s/k3d con al menos 8 GB de RAM libres.
- `kubectl` y `helm` instalados.

## Instalación

### Requisitos Previos
- Cluster k3d/k3s con al menos 8 GB RAM disponibles
- `kubectl` y `helm` configurados
- Acceso a Docker (para crear el cluster k3d)

### Ejecución de HIT #1 (ECK Operator + Elasticsearch + Kibana)

Para levantar el stack completo:

```bash
cd efk
chmod +x install.sh
./install.sh
```

El script automáticamente:
1. Crea namespaces `elastic` y `elastic-system`
2. Instala ECK Operator via Helm
3. Despliega Elasticsearch y Kibana via CRDs
4. Configura Fluent Bit como DaemonSet (HIT #2)
5. Aplica ILM policy e index templates

### Ejecución de HIT #2 (Validación de Fluent Bit)

Fluent Bit se instala automáticamente en el paso anterior. Para verificar:

```bash
# Ver que el pod esté en Running
kubectl -n elastic get pods

# Ver logs de Fluent Bit sin errores
kubectl -n elastic logs -l app.kubernetes.io/name=fluent-bit
```

Para validar que Fluent Bit está recolectando logs del scraper:

```bash
# 1. Crear un job del scraper (cuando esté disponible)
kubectl -n ml-scraper create job --from=cronjob/scraper-hourly scraper-test-1

# 2. Acceder a Kibana y verificar en Discover que aparecen logs en el índice scraper-logs-*
```

## Acceso

- **Kibana**: `https://<NODE_IP>:30001`
- **Usuario**: `elastic`
- **Password**: Recuperar con:
  ```bash
  kubectl -n elastic get secret scraper-es-elastic-user -o jsonpath='{.data.elastic}' | base64 -d
  ```

## Hit #2 — Validación de Fluent Bit

Fluent Bit actúa como DaemonSet y procesa los logs JSON del scraper. Para verificar:

1. Ejecutar un Job del scraper:
   ```bash
   kubectl -n ml-scraper create job --from=cronjob/scraper-hourly scraper-efk-test-1
   ```
2. Verificar en Kibana → Discover que los logs aparecen indexados en `scraper-logs-*`.
