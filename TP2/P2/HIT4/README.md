# HIT #4 — Cookbook KQL: 7 queries útiles

## Objetivo

Documentar las consultas operacionales más útiles del scraper usando **KQL (Kibana Query Language)**, el lenguaje de query nativo de Kibana 8+, y compararlo con el equivalente LogQL de la Parte 1.

## KQL vs Lucene Query

Kibana soporta dos lenguajes de query en Discover:

| | KQL (default en Kibana 8+) | Lucene Query (legacy) |
|---|---|---|
| Operadores | `and`, `or`, `not` (case-insensitive) | `AND`, `OR`, `NOT` (mayúsculas) |
| Wildcards | Predecibles, solo al final o en campo | `*` en cualquier posición, más costoso |
| Campos anidados | Soporte nativo | Sintaxis con punto |
| Recomendado | ✅ Sí, Kibana 8+ | ❌ Deprecado desde 8.0 |

**Todas las queries de este HIT usan KQL exclusivamente.** Se incluye el equivalente Lucene en el cookbook a modo comparativo.

## Queries implementadas

El cookbook completo está en [`efk/queries/kql-cookbook.md`](../../../efk/queries/kql-cookbook.md).

| # | Pregunta de negocio | Query KQL |
|---|---|---|
| Q1 | Errores por producto en las últimas 24h | `level: "ERROR" and producto: *` |
| Q2 | Top selectores faltantes (filtros ocultos por ML) | `message: "Filtro * no disponible" and producto: *` |
| Q3 | Distribución de duración del Job | `event: "scrape_completado" and job_duration_ms >= 0` |
| Q4 | Timeouts de Selenium en cualquier producto | `message: *timeout* and (logger: "selenium*" or logger: "extractors")` |
| Q5 | Todos los logs de un CronJob específico | `kubernetes.labels.job_name: "scraper-test-1"` |
| Q6 | Errores sin el ruido de reconexiones de Postgres | `level: "ERROR" and not logger: "psycopg*"` |
| Q7 | Última corrida exitosa por producto | `event: "scrape_completado" and level: "INFO" and items_found >= 1` |

## Cómo correr las queries

1. Abrir Kibana → **Discover**
2. Seleccionar el Data View `scraper-logs`
3. Pegar la query KQL en la barra de búsqueda
4. Ajustar el time range en el time picker (esquina superior derecha)

## Tradeoffs documentados en el cookbook

- **`keyword` field vs `text` field**: búsquedas sobre `level.keyword` o `producto.keyword` son O(1) en el inverted index. Wildcards sobre `message` (campo `text`) requieren full scan de términos.
- **Leading wildcard** (`message: *timeout*`): el más costoso — Elasticsearch no puede usar el índice, debe escanear todos los términos. Aceptable en < 1M docs, problemático a escala.
- **Range query numérica** (`job_duration_ms >= 0`): usa BKD tree (estructura de datos de Lucene para rangos numéricos) — extremadamente eficiente.

## Comparativa con LogQL (Parte 1)

Ver tabla al final de [`efk/queries/kql-cookbook.md`](../../../efk/queries/kql-cookbook.md) con la comparación directa query por query. Diferencia clave: LogQL opera sobre **streams con ventana temporal explícita**; KQL filtra **documentos** y delega el rango temporal al time picker de Kibana.

## Archivos relevantes

| Archivo | Descripción |
|---|---|
| `efk/queries/kql-cookbook.md` | Las 7 queries completas con justificación técnica |
