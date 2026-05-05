# LogQL Cookbook: Consultas Operativas del Scraper

Este documento recopila las consultas LogQL fundamentales para monitorear la salud, el rendimiento y los errores del scraper de MercadoLibre en nuestro clúster k3s. Todas las queries asumen que los logs están siendo recolectados por Promtail bajo el namespace `ml-scraper` y procesados con el parser `| json`.

---

## Q1 — Top errores por producto en las últimas 24h

**1. Pregunta de negocio:** "¿Qué producto está fallando más?" (Útil para priorizar bugfixes de selectores dinámicos).

**2. Query LogQL:**
```logql
sum by (producto) (
  count_over_time(
    {namespace="ml-scraper", app="scraper"} | json | level="ERROR" [24h]
  )
)
```

**3. Output esperado (Formato Tabla en Grafana):**
| producto | value |
|---|---|
| iPhone 16 Pro Max | 14
| bicicleta rodado 29 | 3

**4. Justificación:**
Se utiliza `count_over_time` en lugar de `rate` porque nos interesa el volumen absoluto de errores (cuántos ocurrieron exactamente) en una ventana amplia de 24 horas, no la velocidad por segundo a la que ocurren. El parser `| json` permite a Loki extraer el campo `producto` inyectado vía MDC en Java, lo que habilita la agrupación con sum by `(producto)`.


## Q2 — Tasa de WARNINGs por minuto en la última hora

**1. Pregunta de negocio:**"¿Hubo un pico de errores de retry hace 30 min?" (Visual para detectar incidentes en curso o bloqueos temporales por parte de ML).

**2. Query LogQL:**

```logql
sum by (producto) (
  rate({namespace="ml-scraper", app="scraper"} | json | level="WARNING" [1m])
)
```
**3. Output esperado:**

![alt text](/observability/screenshots/hit3-image.jpg)

- **Eje X:** Muestra la línea de tiempo de la última hora (desde las 10:00 AM hasta las 11:00 AM).
- **Eje Y:** Representa las "Operaciones por segundo" promediadas por minuto.
- **Visualización:** Se observa la línea para "iPhone 16 Pro Max" (azul oscuro) plana cerca de cero, excepto por un pico prominente a las 10:15 AM que alcanza los 0.5 ops/sec, indicando la ráfaga de reintentos. La línea para "bicicleta rodado 29" se mantiene plana.

**4. Justificación:**
Aca sí se utiliza `rate` con una ventana corta `[1m]`. `rate` calcula el número de entradas por segundo, lo que es ideal para dibujar gráficos de series temporales que muestran la "velocidad" a la que el scraper está escupiendo advertencias. Nos permite ver picos (spikes) de bloqueos que la red de ML impone antes de que el scraper logre éxito.

## Q3 — Conteo de filtros que no aparecieron por producto

**1. Pregunta de negocio:** "¿Qué productos pierden el filtro tienda_oficial (ML lo oculta dinámicamente)?"

**2. Query LogQL:**
```logql
sum by (producto) (
  count_over_time(
    {namespace="ml-scraper", app="scraper"}
    | json
    | message =~ "Filtro .* no aplicado.*"
) [7d]
  )
)
```

**3. Output esperado:**
| producto | value |
|---|---|
| iPhone 16 Pro Max | 2 |
| Samsung Galaxy S24 | 5 |

**4. Justificación:**
Utiliza una expresión regular en el mensaje (`message =~ "Filtro .* no disponible"`) para atrapar cualquier tipo de advertencia de filtros faltantes que haya dejado nuestro scraper a lo largo de 7 días. Agrupar la suma por `producto` permite generar rápidamente gráficos de barras o tablas identificando cuáles son los productos conflictivos.

## Q4 — Duración media entre intentos de retry
**1. Pregunta de negocio:** "¿El backoff exponencial está disparando como esperamos?"

**2. Query LogQL:**
```logql
avg_over_time(
  {namespace="ml-scraper", app="scraper"}
    | json
    | message=~"intento.*backoff"
    | unwrap delay_ms
  [1h]
)
```

**3. Output esperado:**

- **Valor:** `4520`
- (Representa un promedio de `~4.5` segundos de delay en la última hora).

**4. Justificación técnica:**
A diferencia de las queries anteriores que cuentan líneas de log, aquí necesitamos extraer un valor matemático alojado dentro del log. El comando `unwrap delay_ms` toma el campo numérico (inyectado en Java mediante `kv("delay_ms", delay)` o MDC) y expone su valor para que funciones de agregación espacial como `avg_over_time` (promedio) puedan operarlo matemáticamente.

## Q5 — Última corrida exitosa por producto
**1. Pregunta de negocio:** "¿Hace cuánto que no scrapeo exitosamente cada producto?" (Base fundamental para configurar una alerta de inactividad).

**2. Query LogQL:**
```logql
{namespace="ml-scraper", app="scraper"}
  | json
  | level="INFO"
  | message="Scrape completado"
```

**3. Output esperado (Formato Tabla de logs crudos):**
| Time | producto | message | items_found | duration_ms
|---|---|---|---|---|
| 2026-05-04 12:00:00 | iPhone 16 Pro Max   | Scrape completado | 30 | 4521
| 2026-05-04 11:58:30 | bicicleta rodado 29 |Scrape completado | 48 | 3210

**4. Justificación técnica:**
Se utiliza la función `topk(1, <log-stream>) by (<label>).` Esta combinación es extremadamente poderosa porque no devuelve una métrica calculada, sino que filtra y devuelve la última línea de log cruda (top 1) que coincida con la condición de éxito, agrupada para cada uno de los productos que el scraper tiene en su base de datos.

# Otras queries interesantes

## Q6 — Percentil 95 de la duración del scrape (Rendimiento)

**1. Pregunta de negocio:** "¿Cuáles son los productos más lentos de procesar, descartando los picos extremos?" (Vital para optimizar tiempos y no comerse timeouts de GitHub Actions)

**2. Query LogQL:**
```logql
quantile_over_time(0.95,
  {namespace="ml-scraper", app="scraper"}
    | json
    | message="Scrape completado"
    | unwrap duration_ms
  [1h]
) by (producto)
```

**3. Output esperado:**

| producto | value (ms) |
|--- | ---|
bicicleta rodado 29 | 6850
iPhone 16 Pro Max | 4120

**4. Justificación técnica:** Usar promedios (`avg_over_time`) es peligroso en rendimiento porque un solo intento que tarde 30 segundos por un problema de red te arruina la métrica. `quantile_over_time(0.95, ...)` calcula el P95, asegurándote que el 95% de tus scrapes para ese producto tardaron menos que ese valor, dándote una foto mucho más realista de la experiencia general.

## Q7 — Anomalía de negocio: Scrapes "exitosos" sin resultados

**1. Pregunta de negocio:** "¿Qué productos se están scrapeando sin errores técnicos pero están devolviendo 0 resultados?" (Fuerte indicador de que MercadoLibre cambió el HTML, nos aplicó un shadow-ban, o el producto fue dado de baja).

**2. Query LogQL:**
```logql
sum by (producto) (
  count_over_time(
    {namespace="ml-scraper", app="scraper"}
      | json
      | level="INFO"
      | message="Scrape completado"
      | items_found = 0
    [24h]
  )
)
```

**3. Output esperado:**

| producto | value (ocurrencias) |
|--- | ---|
rtx 5090 | 12
bicicleta rodado 29 | 1

**4. Justificación técnica:** Aquí filtramos específicamente por un evento de éxito (`level="INFO", message="Scrape completado"`), pero le agregamos una condición de negocio evaluando el valor numérico extraído del JSON: `items_found = 0`. Esto diferencia magistralmente un error técnico (una excepción en Java) de una anomalía de datos (el código corrió perfecto, pero la página estaba vacía).

## Q8 — Ratio de Errores vs Tráfico Total (Salud del Sistema)

**1. Pregunta de negocio:** "¿Qué porcentaje del total de operaciones de mi scraper están fallando en este momento?" (El mejor indicador universal para disparar una alerta general en la madrugada).

**2. Query LogQL:**
```logql
sum(rate({namespace="ml-scraper", app="scraper"} | json | level="ERROR" [5m]))
/
sum(rate({namespace="ml-scraper", app="scraper"} [5m]))
```

**3. Output esperado:**
- Valor: 0.042 (Se formatea en Grafana como 4.2%)
- (Un arco de color verde si está por debajo del 5%, amarillo si pasa el 5%, rojo si pasa el 10%).

**4. Justificación técnica:** LogQL permite hacer operaciones matemáticas cruzadas entre diferentes queries. Aquí estamos dividiendo la tasa de logs de ERROR por la tasa total de logs generados en los últimos 5 minutos. El resultado es un ratio entre 0 y 1 que te da un termómetro inmediato de la salud del sistema, independientemente de si estás haciendo 10 scrapes por minuto o 1000.