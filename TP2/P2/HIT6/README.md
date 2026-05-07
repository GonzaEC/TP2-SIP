# HIT #6 — Alertas via Kibana Alerting (bonus +5%)

## Objetivo

Configurar una **rule de Kibana Alerting** que dispare al detectar más de 5 eventos `level: "ERROR"` del scraper en cualquier ventana de 1 hora, con notificación al mismo canal de Discord que la alerta de Loki (Parte 1). Esto permite que la cátedra valide ambos stacks en el mismo canal.

## Por qué un conector Webhook y no uno Discord nativo

Kibana no tiene un connector "Discord" de primera clase en su catálogo. Sin embargo, Discord acepta webhooks genéricos con cuerpo JSON. El connector de tipo **`.webhook`** de Kibana envía un `POST` con el body configurado — Discord lo recibe e interpreta el campo `content` como mensaje de texto.

> **Nota sobre licencia**: el connector `.webhook` requiere licencia **Trial o Gold+**. `install.sh` activa automáticamente el trial de 30 días vía `POST /_license/start_trial?acknowledge=true` antes de crear el conector.

## Qué se hizo

### 1. Activación de licencia Trial

```bash
curl -sk -u "elastic:$PASSWORD" \
  -X POST "https://localhost:9200/_license/start_trial?acknowledge=true" \
  -H "Content-Type: application/json"
```

Esto desbloquea los connectors `.webhook`, `.email` y `.slack` durante 30 días.

### 2. Conector Discord (via API en `install.sh`)

Se crea automáticamente durante la ejecución del script si `DISCORD_WEBHOOK_URL` está definido:

```bash
CONNECTOR_ID=$(curl -sk -u "elastic:$PASSWORD" \
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
  }" | jq -r '.id')
```

> El `connector_type_id: ".webhook"` es el tipo genérico de Kibana para llamadas HTTP arbitrarias. El prefijo `.` indica que es un connector built-in (no requiere plugin externo).

### 3. Regla de alerta Elasticsearch Query

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
      \"searchType\": \"esQuery\",
      \"esQuery\": \"{\\\"query\\\":{\\\"term\\\":{\\\"level.keyword\\\":\\\"ERROR\\\"}}}\",
      \"size\": 100,
      \"threshold\": [5],
      \"thresholdComparator\": \">\",
      \"timeWindowSize\": 1,
      \"timeWindowUnit\": \"h\"
    },
    \"actions\": [{
      \"id\": \"${CONNECTOR_ID}\",
      \"group\": \"query matched\",
      \"frequency\": {
        \"notify_when\": \"onActiveAlert\",
        \"throttle\": null,
        \"summary\": false
      },
      \"params\": {
        \"body\": \"{\\\"content\\\": \\\"ALERTA SIP 2026 (EFK): {{context.value}} errores del scraper en 1h.\\\"}\"
      }
    }]
  }"
```

**Parámetros clave:**

| Parámetro | Valor | Por qué |
|---|---|---|
| `rule_type_id` | `.es-query` | El tipo built-in de Kibana para queries sobre índices ES |
| `searchType` | `esQuery` | Usa ES Query DSL directo — `kql` fue removido en Kibana 8.x |
| `esQuery` | `{"query":{"term":{"level.keyword":"ERROR"}}}` | Lookup directa en el inverted index — O(1) |
| `thresholdComparator` | `>` | Más de N ocurrencias (no `>=`) |
| `threshold` | `[5]` | Array — el comparador `>` toma el primer elemento |
| `timeWindowSize / Unit` | `1` / `h` | Ventana deslizante de 1 hora |
| `schedule.interval` | `5m` | Kibana evalúa la condición cada 5 minutos |
| `frequency.notify_when` | `onActiveAlert` | Requerido en Kibana 8.8+ — dispara en cada evaluación mientras la alerta sigue activa |
| `{{context.value}}` | número de hits | Variable correcta para el conteo — `{{context.hits}}` devuelve el documento raw de ES |

### 4. Cuerpo del mensaje Discord

```json
{
  "content": "ALERTA SIP 2026 (EFK): {{context.value}} errores del scraper en 1h."
}
```

`{{context.value}}` se sustituye en runtime con el número de documentos que matchearon la query en la ventana de 1h.

## Comparativa con la alerta de Loki (Parte 1)

| | Grafana Alerting (Parte 1) | Kibana Alerting (Parte 2) |
|---|---|---|
| Tipo de query | LogQL sobre streams | ES Query DSL sobre documentos |
| Condición | `count_over_time > 0` | `count > 5` en ventana 1h |
| Frecuencia de check | `for: 1m` (post-firing delay) | `schedule.interval: 5m` |
| Connector | Provisionado via Kubernetes Secret | Creado via API — URL nunca hardcodeada en el repo |
| Canal | Discord (`#alertas-sip`) | Mismo Discord (`#alertas-sip`) |

El tener ambas alertas en el mismo canal permite comparar directamente cuál detecta antes un incidente real.

## Uso

```bash
export DISCORD_WEBHOOK_URL='https://discord.com/api/webhooks/<id>/<token>'
cd efk && ./install.sh
```

Si `DISCORD_WEBHOOK_URL` no está definido, el script omite este HIT sin fallar (mensaje: `-- Hit #6 omitido`).

## Cómo testear

**Opción 1 — Bajar el threshold a 0:**
En Kibana → Stack Management → Alerts and Insights → Rules → editar la regla → cambiar threshold a `0` → guardar → esperar 5 minutos → verificar Discord → restaurar a `5`.

**Opción 2 — Inyectar errores reales via API:**
```bash
PASSWORD=$(kubectl -n elastic get secret scraper-es-elastic-user -o jsonpath='{.data.elastic}' | base64 -d)
kubectl -n elastic port-forward svc/scraper-es-http 9200:9200 &
for i in $(seq 1 6); do
  curl -sk -u "elastic:$PASSWORD" -X POST "https://localhost:9200/scraper-logs-$(date +%Y.%m.%d)/_doc" \
    -H "Content-Type: application/json" \
    -d "{\"@timestamp\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",\"level\":\"ERROR\",\"message\":\"test-error-$i\"}"
done
```

## Archivos relevantes

| Archivo | Descripción |
|---|---|
| `efk/install.sh` | Sección Hit #6: activa trial, crea conector y regla via API |
| `efk/HIT6-README.md` | Documentación extendida con variantes de connectors por licencia |

## Captura de validación

![HIT6 - Discord Alert EFK](/efk/screenshots/hit6-discord-alert-efk.png)
