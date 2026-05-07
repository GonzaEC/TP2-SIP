# HIT #6 — Alertas via Kibana Alerting (bonus +5%)

## Configuración de Alertas EFK

### 1. Connector de Alertas

Kibana Alerting requiere un connector para enviar notificaciones. El connector `.webhook` (necesario para Discord) **requiere licencia Gold o superior** en Kibana 8.x. Con la licencia básica (default en ECK), se usa un connector `.index` que escribe las alertas en un índice de Elasticsearch.

#### Connector creado (`.index`):

```bash
# Crear connector que escribe alertas en índice
curl -sk -u "elastic:$PASSWORD" \
  -X POST "https://localhost:5601/s/default/api/actions/connector" \
  -H "kbn-xsrf: true" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "alert-index",
    "connector_type_id": ".index",
    "config": {
      "index": ".alerts-scraper",
      "executionTimeField": "@timestamp"
    }
  }'
```

#### Para usar webhook con Discord (requiere licencia Gold+):

```bash
# Solo con licencia Gold o superior
curl -sk -u "elastic:$PASSWORD" \
  -X POST "https://localhost:5601/s/default/api/actions/connector" \
  -H "kbn-xsrf: true" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"discord-sip2026\",
    \"connector_type_id\": \".webhook\",
    \"config\": {
      \"url\": \"${DISCORD_WEBHOOK_URL}\",
      \"method\": \"post\",
      \"headers\": { \"Content-Type\": \"application/json\" }
    }
  }"
```

### 2. Regla de Alerta

Regla creada via API que dispara cuando hay **más de 5 errores en 1 hora**:

```bash
curl -sk -u "elastic:$PASSWORD" \
  -X POST "https://localhost:5601/s/default/api/alerting/rule" \
  -H "kbn-xsrf: true" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Scraper: mas de 5 ERRORs en 1h",
    "rule_type_id": ".es-query",
    "consumer": "alerts",
    "schedule": { "interval": "5m" },
    "params": {
      "index": ["scraper-logs-*"],
      "timeField": "@timestamp",
      "searchType": "esQuery",
      "esQuery": "{\"query\":{\"bool\":{\"must\":[{\"term\":{\"level.keyword\":\"ERROR\"}}]}}}",
      "size": 100,
      "threshold": [5],
      "thresholdComparator": ">",
      "timeWindowSize": 1,
      "timeWindowUnit": "h"
    },
    "actions": [{
      "id": "<connector-id>",
      "group": "query matched",
      "frequency": {
        "summary": true,
        "notify_when": "onActionGroupChange"
      },
      "params": {
        "document": {
          "subject": "ALERTA SIP 2026 (EFK): {{context.hits}} errores del scraper en 1h",
          "message": "Producto top: {{context.value}}"
        }
      }
    }]
  }'
```

### 3. Monitor de Alertas → Discord

Como el connector `.webhook` no está disponible con licencia básica, se creó un script `efk/scripts/alert-monitor.py` que:
1. Consulta el índice `.alerts-scraper` cada 5 minutos
2. Envía las alertas nuevas al webhook de Discord

```bash
DISCORD_WEBHOOK_URL=<tu-webhook> ES_PASSWORD=<password> python efk/scripts/alert-monitor.py
```

### 4. Variables de Entorno

| Variable | Descripción | Ejemplo |
|---|---|---|
| `DISCORD_WEBHOOK_URL` | URL del webhook de Discord | `https://discord.com/api/webhooks/...` |
| `ES_PASSWORD` | Password del usuario elastic | Generada por ECK — ver secret `scraper-es-elastic-user` |
| `ES_URL` | URL de Elasticsearch (opcional) | `https://scraper-es-http.elastic.svc:9200` |

### 5. Verificación

1. Ir a **Kibana → Alerts and Insights → Rules**
2. Verificar que la regla "Scraper: mas de 5 ERRORs en 1h" esté activa
3. Para testear, bajar el threshold a `0` temporalmente o generar 6+ errores
4. Ver las alertas en **Alerts and Insights → Alerts**

### Limitación de Licencia

| Feature | Basic License | Gold+ License |
|---|---|---|
| `.index` connector | ✅ | ✅ |
| `.webhook` connector | ❌ | ✅ |
| `.email` connector | ❌ | ✅ |
| `.slack` connector | ❌ | ✅ |

Con licencia básica, las alertas se escriben en un índice y el script `alert-monitor.py` las reenvía a Discord.
