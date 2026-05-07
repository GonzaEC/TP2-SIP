# HIT #4 — LogQL cookbook: 5+ queries útiles

## Objetivo

Documentar al menos 5 queries LogQL que respondan preguntas operativas reales sobre el scraper. Cada query incluye: pregunta de negocio, query LogQL, output esperado y explicación.

## Queries

### Q1 — Top errores por producto en las últimas 24h

**Pregunta**: ¿Qué producto está fallando más?

```logql
sum by (producto) (
  count_over_time(
    {namespace="ml-scraper", app="scraper"} | json | level="ERROR" [24h]
  )
)
```

**Por qué**: `count_over_time` cuenta ocurrencias en la ventana de 24h. `sum by (producto)` agrupa por producto para identificar cuál tiene más errores.

### Q2 — Tasa de WARNINGs por minuto en la última hora

**Pregunta**: ¿Hubo un pico de errores de retry hace 30 min?

```logql
sum by (producto) (
  rate({namespace="ml-scraper", app="scraper"} | json | level="WARNING" [1m])
)
```

**Por qué**: `rate` calcula la tasa por segundo, multiplicado por 60 da warnings/minuto. Útil para detectar incidentes en curso.

### Q3 — Conteo de filtros que no aparecieron por producto

**Pregunta**: ¿Qué productos pierden el filtro `tienda_oficial`?

```logql
sum by (producto) (
  count_over_time(
    {namespace="ml-scraper", app="scraper"}
      | json
      | message =~ "Filtro .* no aplicado"
    [7d]
  )
)
```

**Por qué**: Regex en `message` para capturar el patrón de filtro no aplicado (el scraper loguea `"Filtro 'X' no aplicado en 'Producto Y'"`). Ventana de 7 días para ver tendencias.

### Q4 — Duración media entre intentos de retry

**Pregunta**: ¿El backoff exponencial está disparando como esperamos?

```logql
avg_over_time(
  {namespace="ml-scraper", app="scraper"}
    | json
    | message=~"intento.*backoff"
    | unwrap delay_ms
  [1h]
)
```

**Por qué**: `unwrap delay_ms` extrae el campo numérico del JSON. `avg_over_time` calcula el promedio en la ventana de 1h.

### Q5 — Última corrida exitosa por producto

**Pregunta**: ¿Hace cuánto que no scrapeo exitosamente cada producto?

```logql
topk(1,
  {namespace="ml-scraper", app="scraper"}
    | json
    | level="INFO"
    | message="Scrape completado"
) by (producto)
```

**Por qué**: `topk(1)` devuelve el registro más reciente por producto. Base para la alerta del Hit #6.

## Archivo

Las queries están documentadas en `observability/queries/logql-cookbook.md`.

## Captura de validación

![HIT4 - LogQL Queries](/observability/screenshots/hit4-image.jpg)
