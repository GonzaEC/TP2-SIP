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

![HIT3 - JSON Fields](../../screenshots/hit3-json-fields.png)
