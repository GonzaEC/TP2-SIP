# HIT #3 — Index Pattern + ILM (rollover, retention 7 días)

## Objetivo

Evitar que Elasticsearch acumule índices indefinidamente configurando una política de **Index Lifecycle Management (ILM)** con retención de 7 días, y crear el **Data View** `scraper-logs` para que Kibana pueda visualizar los datos.

## Qué se hizo

### 1. ILM Policy — ciclo hot → warm → delete

**Archivo**: `efk/manifests/ilm-policy.json`

La policy `scraper-logs` define tres fases:

| Fase | Condición de entrada | Acciones |
|------|---------------------|----------|
| **Hot** | `min_age: 0ms` (inmediato) | Rollover a los **1d** o al superar **1GB** de shard. El índice activo recibe escrituras de Fluent Bit. |
| **Warm** | `min_age: 1d` desde el rollover | `shrink` a 1 shard + `forcemerge` a 1 segmento. Índice comprimido, solo lectura. |
| **Delete** | `min_age: 7d` desde el rollover | Eliminación definitiva del índice. |

Equivalente al `retention_period: 168h` de Loki (Parte 1), pero más expresivo: Loki borra en bloque, ILM mueve los datos por fases y comprime antes de eliminar.

### 2. Index Template

Asocia la policy al patrón `scraper-logs-*` y fija la configuración de shards:

```json
{
  "index_patterns": ["scraper-logs-*"],
  "template": {
    "settings": {
      "number_of_shards": 1,
      "number_of_replicas": 0,
      "index.lifecycle.name": "scraper-logs",
      "index.lifecycle.rollover_alias": "scraper-logs"
    }
  }
}
```

> `number_of_replicas: 0` es obligatorio en single-node. Con una réplica, el cluster queda en `yellow` permanente porque no hay otro nodo donde asignar la réplica.

### 3. Data View

El Data View `scraper-logs` (pattern `scraper-logs-*`, timestamp field `@timestamp`) se crea automáticamente al importar el NDJSON del Hit #5 (`efk/dashboards/scraper-overview.ndjson`), donde está incluido como primer objeto.

## Instalación automática

El `install.sh` aplica la policy y el template vía API de Elasticsearch:

```bash
PASSWORD=$(kubectl -n elastic get secret scraper-es-elastic-user -o jsonpath='{.data.elastic}' | base64 -d)
kubectl -n elastic port-forward svc/scraper-es-http 9200:9200 &

# ILM Policy
curl -sk -u "elastic:$PASSWORD" \
  -X PUT "https://localhost:9200/_ilm/policy/scraper-logs" \
  -H "Content-Type: application/json" \
  -d @efk/manifests/ilm-policy.json

# Index Template
curl -sk -u "elastic:$PASSWORD" \
  -X PUT "https://localhost:9200/_index_template/scraper-logs-template" \
  -H "Content-Type: application/json" \
  -d '{ "index_patterns": ["scraper-logs-*"], ... }'
```

## Verificación

### Policy aplicada
```bash
curl -sk -u "elastic:$PASSWORD" "https://localhost:9200/_ilm/policy/scraper-logs" | jq '.scraper-logs.policy.phases | keys'
# ["delete", "hot", "warm"]
```

### Índices con la policy asignada
```bash
curl -sk -u "elastic:$PASSWORD" "https://localhost:9200/_cat/indices/scraper-logs-*?v&h=index,ilm.phase"
# index                       ilm.phase
# scraper-logs-2026.05.05     hot
```

### Forzar rollover en desarrollo (sin esperar 24h)
```bash
curl -sk -u "elastic:$PASSWORD" \
  -X POST "https://localhost:9200/scraper-logs/_rollover" \
  -H "Content-Type: application/json" \
  -d '{"conditions":{"max_age":"0ms"}}'
```

## Validación en Kibana

**Stack Management → Index Lifecycle Policies** → aparece la policy `scraper-logs` con las 3 fases (hot / warm / delete) y sus condiciones de transición.

**Stack Management → Index Management** → cada índice `scraper-logs-YYYY.MM.DD` muestra la columna `Lifecycle policy = scraper-logs` y la fase actual (`hot`).

## Archivos relevantes

| Archivo | Descripción |
|---|---|
| `efk/manifests/ilm-policy.json` | Definición de las 3 fases (hot/warm/delete) |
| `efk/install.sh` | Aplica la policy y el index template vía API |
| `efk/dashboards/scraper-overview.ndjson` | Incluye el Data View `scraper-logs` (importado en Hit #5) |

## Captura de validación

![HIT3 - ILM Policy](/efk/screenshots/hit3-ilm-policy.png)
