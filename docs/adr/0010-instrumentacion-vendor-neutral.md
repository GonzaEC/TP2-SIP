# 0010 — Adoptamos OpenTelemetry como capa de instrumentación vendor-neutral

- Date: 2026-05-25
- Status: Accepted
- Deciders: Gemini CLI (en representación del equipo)

## Contexto

Tras los TP 2 · Partes 1 y 2 tenemos dos stacks completos de logging corriendo en paralelo: Loki (con Promtail) y Elasticsearch (con Fluent Bit). Ambos funcionan, pero:
- Estamos corriendo dos DaemonSets que leen los mismos archivos.
- El scraper usa `python-json-logger` que es un detalle de implementación específico — si mañana migráramos a Datadog o New Relic, habría que cambiar el módulo de logging.
- La decisión "Loki vs Elastic" se siente prematura y arbitraria.

OpenTelemetry es un proyecto CNCF graduated (2023) que define un protocolo (OTLP) y SDKs vendor-neutral. Los 4 grandes proveedores SaaS (Datadog, New Relic, Dynatrace, Splunk) y los OSS (Loki, Elasticsearch, Prometheus, Jaeger, Tempo) soportan OTLP nativo en 2026.

## Decisión

Adoptamos **OpenTelemetry Collector + SDK** como capa de instrumentación unificada. El collector hace fan-out a Loki Y a Elasticsearch en simultáneo (prueba de concepto que el modelo funciona). El scraper se re-instrumenta con el SDK de OTel para Python.

## Consecuencias

- Más fácil: un solo agente por nodo (DaemonSet del collector). Sumar un backend nuevo es 5 líneas de YAML. El código del scraper no cambia (call-sites idénticos al TP 2 · P1).
- Más difícil: una capa más de YAML para mantener (CRDs, config del collector). El SDK de Python es menos maduro que Java/Go — esperar bugs ocasionales. Aprender OTLP, processors, OTTL.
- Sacrificio: más latencia de export (batch processor) vs hablar HTTP/JSON directo a Loki — pero medible solo en sub-ms, irrelevante en un scraper.
- Riesgo: el SDK de Python aún tiene la API de logs marcada "stable since 2024" pero todavía con cambios menores entre minor versions. Pinear `==1.30.x`.

## Referencias

- OTel CNCF graduation: https://www.cncf.io/announcements/2023/11/06/cloud-native-computing-foundation-announces-opentelemetry-graduation/
- OTLP spec: https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/protocol/otlp.md
- Comparativa: TP 2 · Parte 4 (ADR comparativo final)
