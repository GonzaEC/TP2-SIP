# 0009 — Evaluación de EFK como segundo stack de logging

- Date: 2026-05-05
- Status: Proposed
- Deciders: Equipo SIP 2026

## Contexto

En la Parte 1 adoptamos Loki + Promtail + Grafana (ADR 0007). En esta Parte 2 desplegamos EFK (Elasticsearch, Fluent Bit, Kibana) en paralelo con dos objetivos:
1. Obtener datos comparativos reales sobre el mismo workload (scraper).
2. Entender los trade-offs (latencia, footprint, ergonomía) antes de cerrar la decisión final.

Restricciones:
- Cluster k3s single-node, ~8 GB RAM totales.
- Logs JSON del scraper (Hit #3 de Parte 1) compartidos por ambos stacks.
- Licenciamiento: Elasticsearch usa Elastic License v2 (no es estrictamente OSS).

## Decisión

Implementar el stack EFK utilizando el ECK Operator para la gestión del ciclo de vida de Elasticsearch y Kibana. Se elige Fluent Bit como agente de logs por su bajo consumo de recursos (escrito en C) frente a Fluentd.

Se mantienen ambos stacks (Loki y EFK) coexistiendo en el mismo cluster para permitir comparaciones directas en la Parte 4 del TP.

## Consecuencias

- **Positivas**:
  - Capacidad de búsqueda full-text de alta performance gracias al inverted index de Lucene.
  - Visualizaciones avanzadas y análisis exploratorio superior en Kibana.
  - Experiencia práctica con operadores de Kubernetes (ECK).
- **Negativas/Riesgos**:
  - Alto consumo de memoria (Elasticsearch requiere al menos 2GB de RAM).
  - Complejidad operativa adicional (shards, ILM policies, heap tuning).
  - Restricciones de licencia para uso comercial as-a-service.

## Métricas (Estimadas/Objetivo)

- RAM EFK stack: ~2.5 - 3.0 GiB (frente a < 1 GiB de Loki).
- Latencia en búsquedas de texto libre: Milisegundos en EFK vs Segundos/Minutos en Loki.
