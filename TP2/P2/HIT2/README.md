# HIT #2 — Fluent Bit: Pipeline de logs y Parser JSON

## Objetivo

Configurar **Fluent Bit** como DaemonSet para recolectar los logs del scraper, parsear el formato JSON estructurado que definimos en la Parte 1 y enviarlos a Elasticsearch.

## Qué se hizo

### 1. Fluent Bit vs Fluentd

Se eligió **Fluent Bit** (escrito en C) en lugar de Fluentd (Ruby) por su extrema ligereza: ~40MB de RAM vs ~400MB. En un cluster de 8GB, esta diferencia es crítica para poder correr el stack EFK y Loki simultáneamente.

### 2. Pipeline explícito (Input → Parser → Filter → Output)

**Archivo**: `efk/helm/fluent-bit-values.yaml`

#### A. Input (tail)
Lee los logs crudos de `/var/log/containers/*ml-scraper*.log`. Se usa el parser `cri` para extraer el stream y el timestamp base de Kubernetes.

#### B. Parser JSON (json_scraper)
Este es el paso clave. Los logs del scraper ya vienen en JSON estructurado (Hit #3 de P1). El parser extrae los campos de primer nivel:
```ini
[PARSER]
    Name         json_scraper
    Format       json
    Time_Key     timestamp
    Time_Format  %Y-%m-%dT%H:%M:%S%z
```

#### C. Filter (kubernetes & grep)
- **kubernetes**: Enriquece los logs con el nombre del pod, namespace y labels.
- **grep**: Filtra y descarta cualquier log que no pertenezca al label `app=scraper`, asegurando que Elasticsearch solo reciba logs del negocio.

#### D. Output (es)
Envía los datos a `scraper-es-http.elastic.svc.cluster.local:9200`. Usa la convención `Logstash_Prefix scraper-logs` para generar índices diarios como `scraper-logs-2026.05.05`.

## Verificación

1. Generar logs reales:
```bash
kubectl -n ml-scraper create job --from=cronjob/scraper-hourly scraper-efk-test-1
```

2. Verificar ingestión:
```bash
# Ver índices creados
PASSWORD=$(kubectl -n elastic get secret scraper-es-elastic-user -o jsonpath='{.data.elastic}' | base64 -d)
curl -sk -u "elastic:$PASSWORD" "https://localhost:9200/_cat/indices?v" | grep scraper
```

## Validación en Kibana

En Kibana → **Discover**, tras crear el Data View (Hit #3), se deben ver los campos parseados:
- `level`: INFO/ERROR
- `producto`: iphone, etc.
- `logger`: ar.edu.sip...
- `kubernetes.pod_name`: scraper-efk-test-1-xxxx

## Versiones pinneadas

| Componente | Chart | Versión |
|---|---|---|
| Fluent Bit | `fluent/fluent-bit` | 0.48.5 |
| App version | `fluent-bit` | 3.2.4 |

## Archivos relevantes

| Archivo | Descripción |
|---|---|
| `efk/helm/fluent-bit-values.yaml` | Configuración completa del pipeline |
| `efk/install.sh` | Instalación automatizada del chart |

## Captura de validación

![HIT2 - Discover](/efk/screenshots/hit2-fluentbit-discover.png)
