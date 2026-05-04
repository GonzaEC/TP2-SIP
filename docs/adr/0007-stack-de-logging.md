# 0007 — Adoptamos Loki + Promtail + Grafana para logging centralizado

- Date: 2026-05-04
- Status: Accepted
- Deciders: Equipo SIP 2026

## Contexto

El scraper corre como CronJob en k3s y emite logs a stdout. `kubectl logs` se vuelve inutilizable en cuanto los pods son recolectados. Necesitamos un backend de logs con retención mínima 7 días, queryable, y visualizable. Restricciones:

- Cluster local k3s single-node, ~6 GB RAM disponibles.
- Sin cloud / sin servicios pagos.
- Equipo familiarizado con Grafana.

Alternativas consideradas: Loki+Promtail, Loki+Alloy, Vector+Loki, OTel+Loki, EFK, Datadog, Splunk. Tabla comparativa en TP 2 · Parte 1 / Material de apoyo.

## Decisión

Adoptamos **Loki + Promtail + Grafana** (charts separados, versiones pinneadas).

## Consecuencias

- **Más fácil**: setup en ~10 min con Helm; integración nativa con dashboards Grafana; costo $0; modelo label-first es simple y suficiente para nuestro volumen.
- **Más difícil**: full-text grep es lento (Loki indexa labels, no el cuerpo del log) — si en el futuro queremos búsquedas tipo "encontrame el log con esta substring de 100 chars" vamos a sufrir.
- **Sacrificio**: no podemos hacer queries complejas tipo SQL (vs Splunk SPL).
- **Riesgo**: cardinality explosion si labelean mal. Mitigado en Hit #2 con regla de ≤10 labels totales y ningún label de cardinalidad alta.

## Referencias

- Loki design doc: https://grafana.com/docs/loki/latest/get-started/architecture/
- Comparativa de la cátedra: TP 2 / Material de apoyo
