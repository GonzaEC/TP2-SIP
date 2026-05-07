# HIT #3 — Migrar el scraper a logs JSON estructurados

## Objetivo

Migrar el módulo de logging del scraper (Java) para emitir **JSON line-delimited a stdout** en lugar de texto plano, permitiendo que Loki extraiga campos automáticamente y se puedan hacer queries con `| json`.

## Qué se hizo

El scraper es una aplicación Java que usa **SLF4J + Logback**. Se reemplazó el `PatternLayout` (texto plano) por el `LogstashEncoder` de la biblioteca `logstash-logback-encoder`.

### 1. Dependencia en `pom.xml`

**Archivo**: `TP1/HIT8/pom.xml`

```xml
<!-- JSON structured logging (Hit #3) -->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

### 2. Cambio en `logback.xml`

**Archivo**: `TP1/HIT8/src/main/resources/logback.xml`

Se reemplazó el appender CONSOLE de `PatternLayout` (texto plano) por `LogstashEncoder` (JSON). El appender FILE queda en texto plano para debugging local dentro del pod.

```xml
<appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
  <encoder class="net.logstash.logback.encoder.LogstashEncoder">
    <fieldNames>
      <timestamp>timestamp</timestamp>
      <level>level</level>
      <logger>logger</logger>
      <message>message</message>
      <stackTrace>stack_trace</stackTrace>
    </fieldNames>
    <!-- Campos MDC incluidos como campos JSON de primer nivel -->
    <includeMdcKeyName>producto</includeMdcKeyName>
    <includeMdcKeyName>browser</includeMdcKeyName>
    <includeMdcKeyName>intento</includeMdcKeyName>
  </encoder>
</appender>
```

### 3. Enriquecimiento con MDC y StructuredArguments

**Archivo**: `TP1/HIT8/src/main/java/ar/edu/sip/MercadoLibreScraper.java`

Se usan dos mecanismos para agregar campos estructurados:

**MDC** (`org.slf4j.MDC`) — para campos que aplican a todo un bloque de logs:
```java
MDC.put("browser", browser);     // aparece en todos los logs de la ejecución
MDC.put("producto", producto);   // aparece en todos los logs de ese producto
MDC.put("intento", String.valueOf(intento)); // aparece en logs del retry actual
```

**StructuredArguments.kv()** — para campos puntuales de un evento:
```java
LOG.info("Scrape completado", kv("items_found", resultados.size()), kv("duration_ms", duracionMs));
LOG.warn("Filtro no aplicado", kv("filtro", texto), kv("error_msg", e.getMessage()));
```

`PostgresWriter.java` usa los mismos patrones. Como el MDC de `producto` ya está puesto en `MercadoLibreScraper` antes de llamar a `PostgresWriter.guardar()`, todos sus logs heredan ese campo automáticamente.

### 4. Resultado en stdout (JSON por línea)

```json
{"timestamp":"2026-05-07T01:17:35Z","level":"INFO","logger":"ar.edu.sip.MercadoLibreScraper","message":"Scrape completado","browser":"chrome","producto":"GeForce RTX 5090","intento":"1","items_found":3,"duration_ms":63324}
```

### 5. Validación con LogQL

En Grafana → Explore → datasource Loki:

```logql
{namespace="ml-scraper"} | json
```

En el panel **Detected fields** aparecen: `level`, `producto`, `browser`, `logger`, `message`, `timestamp`, `intento`.

Para filtrar por producto específico:
```logql
{namespace="ml-scraper"} | json | producto="GeForce RTX 5090"
```

## Antes vs Después

| Formato | Ejemplo de log |
|---|---|
| **Antes** (texto plano) | `01:02:49.615 INFO  [a.e.s.MercadoLibreScraper] JSON guardado: /app/output/geforce_rtx_5090.json` |
| **Después** (JSON) | `{"timestamp":"2026-05-07T01:17:35Z","level":"INFO","logger":"ar.edu.sip.MercadoLibreScraper","message":"JSON guardado","producto":"GeForce RTX 5090","browser":"chrome"}` |

## Archivos modificados

| Archivo | Cambio |
|---|---|
| `TP1/HIT8/pom.xml` | Agrega dependencia `logstash-logback-encoder 7.4` |
| `TP1/HIT8/src/main/resources/logback.xml` | CONSOLE usa `LogstashEncoder`; FILE mantiene texto plano |
| `TP1/HIT8/src/main/java/ar/edu/sip/MercadoLibreScraper.java` | MDC + `StructuredArguments.kv()` |
| `TP1/HIT8/src/main/java/ar/edu/sip/PostgresWriter.java` | `kv()` en logs de éxito y error |

## Capturas de validación

### Antes — logs en texto plano (sin LogstashEncoder)

![HIT3 - Antes](/observability/screenshots/hit3-antes.png)

### Después — logs JSON estructurados con campos extraídos por `| json`

![HIT3 - Después](/observability/screenshots/hit3-despues.png)
