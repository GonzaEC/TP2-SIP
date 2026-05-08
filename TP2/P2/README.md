# TP 2 · Parte 2 — Logging centralizado con EFK

Stack **EFK** (Elasticsearch + Fluent Bit + Kibana) desplegado en paralelo con el stack Loki de Parte 1. Ambos stacks leen los mismos logs del scraper desde el namespace `ml-scraper`, lo que permite comparar latencia, footprint y ergonomía con datos reales.

## Hits

| Hit | Descripción | Estado |
|-----|-------------|--------|
| [HIT #1](HIT1/README.md) | Deploy del ECK Operator + Elasticsearch single-node + Kibana | ✅ |
| [HIT #2](HIT2/README.md) | Fluent Bit como DaemonSet — pipeline Input→Parser→Filter→Output | ✅ |
| [HIT #3](HIT3/README.md) | Index Pattern + ILM (hot→warm→delete, retención 7 días) | ✅ |
| [HIT #4](HIT4/README.md) | Cookbook KQL: 7 queries operacionales documentadas | ✅ |
| [HIT #5](HIT5/README.md) | Dashboard Kibana provisionado as-code (NDJSON + import API) | ✅ |
| [HIT #6](HIT6/README.md) | Alertas via Kibana Alerting → Discord (bonus +5%) | ✅ |

## Levantar el stack

```bash
cd efk
chmod +x install.sh
./install.sh
```

Ver [efk/README.md](../../efk/README.md) para detalles completos y verificación de cada hit.

## Namespaces

| Namespace | Stack | Acceso |
|-----------|-------|--------|
| `observability` | Loki + Promtail + Grafana (Parte 1) | `http://<NODE_IP>:30000` |
| `elastic` | Elasticsearch + Fluent Bit + Kibana (Parte 2) | `https://<NODE_IP>:30001` |
| `elastic-system` | ECK Operator | — |
