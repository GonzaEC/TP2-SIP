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

Para levantar el stack completo (Hit #1 al #3):

```bash
chmod +x install.sh
./install.sh
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
