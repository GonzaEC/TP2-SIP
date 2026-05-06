# KQL Cookbook: Consultas Operativas del Scraper en Kibana

Este documento recopila las consultas KQL (Kibana Query Language) fundamentales para monitorear la salud, el rendimiento y los errores del scraper de MercadoLibre desde Kibana 8.17. Todas las queries asumen que existe el Data View `scraper-logs` apuntando al índice pattern `scraper-logs-*` con timestamp field `@timestamp`.

> **KQL vs Lucene query**: Lucene es el lenguaje legacy (`field:value AND field:value`). KQL es el moderno (`field: value and field: value`), case-insensitive en operadores, soporta wildcards más predecibles, y es el default de Kibana 8+. **Todas las queries de este cookbook usan KQL exclusivamente.** Se incluye el equivalente Lucene en cada query a modo comparativo — la cátedra valora que sepan que existe la diferencia.

---

## Q1 — Errores por producto en las últimas 24h

**1. Pregunta de negocio:** "¿Qué producto está fallando más?" (Útil para priorizar bugfixes de selectores dinámicos).

**2. Query KQL:**
```
level: "ERROR" and producto: *
```
*(Ajustar el time picker a "Last 24 hours". Visualizar como bar chart vertical agrupado por `producto.keyword`.)*

**3. Equivalente Lucene:**
```
level:"ERROR" AND _exists_:producto
```

**4. Screenshot:**

![Q1 - Errores por producto (last 24h)](/efk/screenshots/hit4-q1.png)

**5. Comentario:**
`producto: *` filtra documentos donde el campo `producto` existe con cualquier valor — equivale al `_exists_` de Lucene. Aunque los wildcards pueden ser costosos en general, sobre el campo `producto.keyword` (tipo `keyword` en el mapping) Elasticsearch los resuelve con una única pasada de diccionario de términos: O(n términos únicos), no O(n documentos). El campo `level` también es `keyword`, así que `level: "ERROR"` es una lookup directa en el inverted index, sin análisis de texto — la combinación es muy eficiente.

---

## Q2 — Top selectores faltantes (filtros que el scraper no encontró)

**1. Pregunta de negocio:** "¿Qué productos pierden filtros como 'Nuevo' o 'Solo tiendas oficiales' (ML los oculta dinámicamente)?"

**2. Query KQL:**
```
message: *no aplicado* and producto: *
```
*(Visualizar como tabla con columnas `producto.keyword` y count, top 20.)*

**3. Equivalente Lucene:**
```
message:*no aplicado* AND _exists_:producto
```

**4. Screenshot:**

![Q2 - Filtros faltantes por producto](/efk/screenshots/hit4-q2.png)

**5. Comentario:**
El scraper genera logs WARN con formato `"Filtro 'Nuevo' no aplicado en 'GeForce RTX 5090'"`. Usamos `*no aplicado*` como wildcard en `message` (tipo `text`) para capturar todas las variantes de filtros fallidos. El campo `producto` es extraído por Fluent Bit (filtro Lua) desde el mensaje. Aunque los wildcards en campos `text` son costosos (full scan de términos), para el volumen del scraper (< 1M docs/día) es aceptable (< 500ms). La alternativa eficiente sería indexar un campo `filter_name: keyword` y buscar `filter_name: "Nuevo"` — O(1) en el inverted index.

---

## Q3 — Distribución de eventos del Job por tipo

**1. Pregunta de negocio:** "¿Cuántos eventos genera cada ejecución? ¿Están balanceados los tipos de evento (scrape_iniciado vs scrape_completado)?"

**2. Query KQL:**
```
event: *
```
*(Visualizar como pie chart agrupado por `event.keyword`. El campo `job_duration_ms` no es emitido por el scraper actualmente; se usa `event` como proxy de la actividad del job.)*

**3. Equivalente Lucene:**
```
_exists_:event
```

**4. Screenshot:**

![Q3 - Distribución de eventos del Job](/efk/screenshots/hit4-q3.png)

**5. Comentario:**
El scraper no emite `job_duration_ms` como campo numérico. En su lugar, Fluent Bit extrae el campo `event` (valores: `scrape_iniciado`, `scrape_completado`) mediante un filtro Lua que detecta patrones en el mensaje (`"Iniciando:"` → `scrape_iniciado`, `"JSON guardado:"` → `scrape_completado`). La query `event: *` usa el wildcard `*` sobre el campo `event.keyword` — Elasticsearch lo resuelve con una pasada del diccionario de términos: O(n términos únicos). La diferencia con LogQL es notable: en Parte 1 necesitábamos `| unwrap job_duration_ms` para extraer el número del texto; acá el campo ya está indexado como keyword por el filtro Lua de Fluent Bit.

---

## Q4 — Logs con timeout de Selenium en cualquier producto

**1. Pregunta de negocio:** "¿Con qué frecuencia falla el browser por timeout? ¿Es una tendencia o un evento aislado?"

**2. Query KQL:**
```
message: *timeout* and (logger: "selenium*" or logger: "extractors")
```

**3. Equivalente Lucene:**
```
message:*timeout* AND (logger:selenium* OR logger:extractors)
```

**4. Screenshot:**

![Q4 - Timeouts de Selenium en Discover](/efk/screenshots/hit4-q4.png)

**5. Comentario:**
El **leading wildcard** en `message: *timeout*` es la query más costosa de este cookbook: Elasticsearch no puede usar el inverted index directamente — debe hacer un full scan de todos los términos del campo, buscando cuáles contienen "timeout". En un índice de < 1M docs se resuelve en < 1s, pero en producción a escala esto se reemplazaría por un campo estructurado `exception_type: "TimeoutException"` de tipo `keyword` (lookup O(1)). La query está escrita así intencionalmente para demostrar el tradeoff: **full-text search flexible vs campo keyword eficiente**.

---

## Q5 — Eventos del CronJob específico (correlación por job_name)

**1. Pregunta de negocio:** "Quiero ver todos los logs de la corrida `scraper-run-1` para debuggear un fallo puntual, correlacionando todos sus módulos."

**2. Query KQL:**
```
kubernetes.labels.job-name: "scraper-run-1"
```
*(Nota: el campo usa guión `job-name`, no underscore `job_name`, porque Fluent Bit preserva el nombre original del label de Kubernetes.)*

**3. Equivalente Lucene:**
```
kubernetes.labels.job-name:"scraper-run-1"
```

**4. Screenshot:**

![Q5 - Correlación por job_name en Discover](/efk/screenshots/hit4-q5.png)

**5. Comentario:**
`kubernetes.labels.job-name` es un campo `keyword` enriquecido automáticamente por el **Fluent Bit Kubernetes filter** con la metadata del pod. Al ser `keyword`, la lookup es una operación directa en el inverted index — sub-milisegundo sin importar el tamaño del índice. Esta es la query "de guardia": cuando llega una alerta a Discord a las 3AM, lo primero es filtrar por el `job-name` del run fallido y ver toda la secuencia (`INFO Iniciando → WARNING Filtro no encontrado → ERROR Se agotaron los reintentos`) en una sola pantalla. Esta correlación **no tiene equivalente simple en LogQL de Parte 1** — Loki requeriría que `job_name` sea un label de stream (definido en Promtail), lo cual no está configurado. Ventaja clara de EFK para debugging post-mortem.

---

## Q6 — Errores que NO sean del módulo Postgres (excluir false positives conocidos)

**1. Pregunta de negocio:** "Quiero ver los errores reales del scraper sin el ruido de reconexiones de Postgres que ya sabemos que son inofensivas."

**2. Query KQL:**
```
level: "ERROR" and not logger: "psycopg*"
```

**3. Equivalente Lucene:**
```
level:"ERROR" AND NOT logger:psycopg*
```

**4. Screenshot:**

![Q6 - Errores sin módulo Postgres](/efk/screenshots/hit4-q6.png)

**5. Comentario:**
`not logger: "psycopg*"` se traduce internamente al `must_not` del Query DSL de Elasticsearch, aplicado sobre el campo `logger.keyword`. La combinación `level: "ERROR"` (must) + `not logger: "psycopg*"` (must_not) es exactamente como Kibana genera el DSL subyacente — dos lookups de keyword en el inverted index operadas con boolean algebra. En LogQL de Parte 1, el equivalente filtra a nivel de línea completa (`| message != "psycopg"`), lo que es menos preciso: podría descartar una línea de ERROR real cuyo mensaje mencione "psycopg" como contexto. EFK filtra por campo, no por la línea entera — más quirúrgico.

---

## Q7 — Últimas corridas exitosas por producto (bonus)

**1. Pregunta de negocio:** "¿Hace cuánto que no se scrapea exitosamente cada producto?" (Base para configurar alertas de inactividad).

**2. Query KQL:**
```
event: "scrape_completado" and level: "INFO"
```
*(Visualizar en Kibana Lens como tabla con `max(@timestamp) by producto.keyword`.)*

**3. Equivalente Lucene:**
```
event:"scrape_completado" AND level:"INFO"
```

**4. Screenshot:**

![Q7 - Última corrida exitosa por producto](/efk/screenshots/hit4-q7.png)

**5. Comentario:**
El scraper no emite `items_found` como campo numérico. En su lugar, usamos `event: "scrape_completado"` (extraído por el filtro Lua de Fluent Bit al detectar `"JSON guardado:"` en el mensaje) combinado con `level: "INFO"` para filtrar solo las corridas exitosas. La combinación de dos condiciones (`event` y `level`) es un ejemplo de **filter context** de Elasticsearch: Kibana ejecuta ambas como filtros cacheables, no como queries de scoring. El equivalente en LogQL (Q5 del cookbook de Parte 1) usaba `topk(1, ...)` — LogQL no tiene `max aggregation` sobre campos de timestamp, requería una sintaxis más verbosa.

---

## Comparativa KQL (Parte 2) vs LogQL (Parte 1)

| Query | LogQL — Parte 1 | KQL — Parte 2 | Diferencia clave |
|---|---|---|---|
| Q1 — Errores por producto | `sum by (producto) (count_over_time({...} \| level="ERROR" [24h]))` | `level: "ERROR" and producto: *` | LogQL opera sobre **streams** con ventana temporal en la query. KQL filtra **documentos** y delega el rango al time picker |
| Q2 — Filtros faltantes | `message =~ "Filtro .* no aplicado.*"` (regex re2) | `message: *no aplicado* and producto: *` | Regex en LogQL vs wildcard en KQL — costo similar, modelo distinto |
| Q3 — Eventos del Job | `avg_over_time(... \| unwrap job_duration_ms [1h])` | `event: *` (pie chart por tipo) | LogQL necesita `unwrap` para extraer el número; en EFK el campo `event` ya está indexado como keyword por el filtro Lua |
| Q4 — Timeouts | `message=~".*timeout.*"` | `message: *timeout*` | Leading wildcard costoso en ambos — Loki es grep lineal, ES es scan de términos |
| Q5 — Por job | Sin equivalente simple (job_name no es label de stream en Loki) | `kubernetes.labels.job-name: "scraper-run-1"` | **Ventaja EFK**: Fluent Bit enriquece con metadata k8s automáticamente |
| Q6 — NOT logger | `\| logger != "psycopg2"` (filtro de línea) | `not logger: "psycopg*"` (filtro de campo) | EFK filtra por campo exacto — más quirúrgico que el filtro de línea de LogQL |
