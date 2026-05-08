# TP 2 · Parte 3 — OpenTelemetry

Este módulo centraliza la recolección de telemetría (logs, traces) utilizando **OpenTelemetry**.

## Arquitectura

Se despliega un **OpenTelemetry Collector** en modo `DaemonSet` (Agente) que:
1. Recolecta logs de los Pods en `/var/log/pods/`.
2. Procesa y enriquece los logs con metadata de Kubernetes.
3. (Próximamente) Realiza fan-out hacia Loki y Elasticsearch.

## Pre-requisitos

- Stack Loki (Parte 1) y EFK (Parte 2) operativos.
- `cert-manager` instalado (usado por el OTel Operator).

## Instalación

```bash
cd otel && ./install.sh
```

## Verificación Hit #2

```bash
# Ver logs del collector para confirmar que recibe datos
kubectl -n otel logs ds/agent-collector
```
