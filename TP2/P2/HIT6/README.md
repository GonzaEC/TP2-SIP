# HIT #6 — Alertas via Kibana Alerting (bonus +5%)

## Objetivo

Configurar una **rule de Kibana Alerting** que dispare al detectar más de 5 eventos `level: "ERROR"` del scraper en cualquier ventana de 1 hora, con notificación al mismo canal de Discord que la alerta de Loki (Parte 1). Esto permite que la cátedra valide ambos stacks en el mismo canal.

## Por qué un conector Webhook y no uno Discord nativo

Kibana no tiene un connector "Discord" de primera clase en su catálogo. Sin embargo, Discord acepta webhooks genéricos con cuerpo JSON. El connector de tipo **`.webhook`** de Kibana envía un `POST` con el body configurado — Discord lo recibe e interpreta el campo `content` como mensaje de texto.

## Qué se hizo

### 1. Conector Discord (via API en `install.sh`)

Se crea automáticamente durante la ejecución del script si `DISCORD_WEBHOOK_URL` está definido:

```bash
curl -sk -u "elastic:$PASSWORD" \
  -X POST "https://localhost:5601/api/actions/connector" \
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

> El `connector_type_id: ".webhook"` es el tipo genérico de Kibana para llamadas HTTP arbitrarias. El prefijo `.` indica que es un connector built-in (no requiere plugin externo).

### 2. Regla de alerta Elasticsearch Query (KQL)

```bash
curl -sk -u "elastic:$PASSWORD" \
  -X POST "https://localhost:5601/api/alerting/rule" \
  -H "kbn-xsrf: true" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"Scraper: mas de 5 ERRORs en 1h\",
    \"rule_type_id\": \".es-query\",
    \"consumer\": \"alerts\",
    \"schedule\": { \"interval\": \"5m\" },
    \"params\": {
      \"index\": [\"scraper-logs-*\"],
      \"timeField\": \"@timestamp\",
      \"searchType\": \"kql\",
      \"kqlQuery\": \"level: \\\"ERROR\\\"\",
      \"size\": 100,
      \"threshold\": [5],
      \"thresholdComparator\": \">\",
      \"timeWindowSize\": 1,
      \"timeWindowUnit\": \"h\"
    },
    \"actions\": [ ... ]
  }"
```

**Parámetros clave:**

| Parámetro | Valor | Por qué |
|---|---|---|
| `rule_type_id` | `.es-query` | El tipo built-in de Kibana para queries sobre índices ES |
| `searchType` | `kql` | Usa KQL directamente (disponible desde Kibana 8.3) en lugar de DSL raw |
| `kqlQuery` | `level: "ERROR"` | Equivalente a la query Q1 del cookbook — campo `keyword`, lookup O(1) |
| `thresholdComparator` | `>` | Más de N ocurrencias (no `>=`) |
| `threshold` | `[5]` | Array — el comparador `>` toma el primer elemento |
| `timeWindowSize` / `timeWindowUnit` | `1` / `h` | Ventana deslizante de 1 hora |
| `schedule.interval` | `5m` | Kibana evalúa la condición cada 5 minutos |

### 3. Cuerpo del mensaje Discord

```json
{
  "content": "ALERTA SIP 2026 (EFK): {{context.hits}} errores del scraper en 1h. Producto top: {{context.value}}"
}
```

`{{context.hits}}` y `{{context.value}}` son templates de Kibana Alerting — se sustituyen en runtime con el número de hits y el valor más alto de la agregación.

## Comparativa con la alerta de Loki (Parte 1)

| | Grafana Alerting (Parte 1) | Kibana Alerting (Parte 2) |
|---|---|---|
| Tipo de query | LogQL sobre streams | KQL sobre documentos ES |
| Condición | `count_over_time > 0` | `count > 5` en ventana 1h |
| Frecuencia de check | `for: 1m` (post-firing delay) | `schedule.interval: 5m` |
| Connector | `grafana-alerts-secret` (Kubernetes Secret) | Creado via API, URL nunca sale del cluster |
| Canal | Discord (`#alertas-sip`) | Mismo Discord (`#alertas-sip`) |

El tener las dos alertas en el mismo canal permite comparar directamente cuál detecta antes un incidente real.

## Uso

```bash
export DISCORD_WEBHOOK_URL='https://discord.com/api/webhooks/<id>/<token>'
cd efk && ./install.sh
```

Si `DISCORD_WEBHOOK_URL` no está definido, el script omite este HIT sin fallar (mensaje: `-- Hit #6 omitido`).

## Cómo testear

**Opción 1 — Bajar el threshold a 0:**
En Kibana → Stack Management → Alerts and Insights → Rules → editar la regla → cambiar threshold a `0` → guardar → esperar 5 minutos → verificar Discord → restaurar a `5`.

**Opción 2 — Generar errores reales:**
```bash
for i in $(seq 1 6); do
  kubectl -n ml-scraper create job --from=cronjob/scraper-hourly "scraper-err-test-$i"
done
```

## Archivos relevantes

| Archivo | Descripción |
|---|---|
| `efk/install.sh` | Sección Hit #6: crea conector y regla via API (solo si `DISCORD_WEBHOOK_URL` definido) |
| `efk/README.md` | Documentación de `DISCORD_WEBHOOK_URL` |

## Captura de validación

![HIT6 - Discord Alert EFK](/efk/screenshots/hit6-discord-alert-efk.png)
