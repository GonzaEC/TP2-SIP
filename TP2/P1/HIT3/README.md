# HIT #3 — Migrar el scraper a logs JSON estructurados

## Objetivo

Migrar el módulo de logging del scraper para emitir **JSON line-delimited a stdout** en lugar de texto plano, permitiendo que Loki extraiga campos automáticamente y se puedan hacer queries con `| json`.

## Qué se hizo

### 1. Cambio en `logging_setup.py`

Se reemplazó el formatter de texto plano por `JsonFormatter` de `python-json-logger >= 3.2.0`:

```python
from pythonjsonlogger.json import JsonFormatter

json_formatter = JsonFormatter(
    "%(asctime)s %(levelname)s %(name)s %(message)s",
    rename_fields={"asctime": "timestamp", "levelname": "level", "name": "logger"},
    timestamp=True,
)
```

### 2. Enriquecimiento con `extra=`

Los call-sites ahora pasan contexto estructurado:

```python
logger.info(
    "Scrape iniciado",
    extra={"producto": producto, "browser": browser, "page": page},
)
logger.error(
    "Timeout tras 3 reintentos",
    extra={"producto": producto, "selector": selector, "attempts": 3},
    exc_info=True,
)
```

### 3. Validación con LogQL

En Grafana → Explore:

```
{namespace="ml-scraper", app="scraper"} | json
```

En el panel **Detected fields** deben aparecer: `level`, `producto`, `browser`, `logger`, `message`, `timestamp`.

## Antes vs Después

| Formato | Ejemplo |
|---|---|
| **Plain text (antes)** | `2026-05-10T03:14:22-0300 \| INFO \| extractors \| Scrapeando página 1` |
| **JSON (después)** | `{"timestamp": "2026-05-10T03:14:22Z", "level": "INFO", "logger": "extractors", "message": "Scrape iniciado", "producto": "iphone", "browser": "chrome", "page": 1}` |

## Captura de validación

![HIT3 - JSON Fields](/observability/screenshots/hit3-json-fields.png)

## QUE SE CAMBIO
```bash
TP1/HIT8/pom.xml
TP1/HIT8/src/main/java/ar/edu/sip/MercadoLibreScraper.java
TP1/HIT8/src/main/java/ar/edu/sip/PostgresWriter.java
TP1/HIT8/src/main/resources/logback.xml
```
Se agrego logstash.encoder.version 7.4 al pom.xml
logback.xml se modifico el CONSOLE para que cambe de encoder. El FILE queda igual (texto plano para debugging local). Solo el CONSOLE pasa de PatternLayout a LogstashEncoder, que es quien emite el JSON que Promtail va a leer.
MercadoLibreScraper.java tiene 2 mecanismos nuevos:
* MDC (org.slf4j.MDC) para campos que aplican a todo un bloque de logs:
```
MDC.put("browser", browser) → aparece en todos los logs de la ejecución
MDC.put("producto", producto) → aparece en todos los logs de ese producto
MDC.put("intento", ...) → aparece en logs del retry actual
```
* kv() (StructuredArguments.kv) para campos puntuales de un evento:
```java
javaLOG.info("Scrape completado", kv("items_found", resultados.size()), kv("duration_ms", duracionMs));
LOG.warn("Filtro no aplicado", kv("filtro", texto), kv("error_msg", e.getMessage()));
```
El resultado en stdout es:
```bash
json{"timestamp":"2026-05-04T12:00:00Z","level":"INFO","logger":"ar.edu.sip.MercadoLibreScraper",
 "message":"Scrape completado","browser":"chrome","producto":"iPhone 16 Pro Max",
 "items_found":30,"duration_ms":4521}
```
En Loki esto queda disponible con: `{namespace="ml-scraper"} | json | producto="iPhone 16 Pro Max"`.

PostgresWriter.java usa los mismos patrones, sin MDC propio. Como el MDC de producto ya está puesto en MercadoLibreScraper antes de llamar a `PostgresWriter.guardar()`, todos sus logs heredan ese campo automáticamente. Solo se agregó `kv()` en los logs de éxito y error.