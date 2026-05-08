# TP2-SIP

Trabajo Práctico Nº 2 — Seminario de Integración Profesional 2026

## TP 2 · Parte 1 — Observabilidad con Loki

Logging centralizado del scraper en k3s con **Loki + Promtail + Grafana**.

```bash
export GRAFANA_ADMIN_PASSWORD='<tu-password-segura>'
# Opcional — solo para el Hit #6 (bonus):
export DISCORD_WEBHOOK_URL='https://discord.com/api/webhooks/<id>/<token>'

cd observability && ./install.sh
```

Ver [`TP2/P1/README.md`](TP2/P1/README.md) o [`observability/README.md`](observability/README.md) para detalles completos.

## TP 2 · Parte 2 — Logging centralizado con EFK

Stack **EFK (Elasticsearch + Fluent Bit + Kibana)** corriendo en paralelo con el stack Loki de Parte 1. Ambos stacks leen los mismos logs del scraper, lo que permite comparar latencia, footprint y ergonomía con datos reales.

```bash
# Opcional — solo para el Hit #6 (bonus):
export DISCORD_WEBHOOK_URL='https://discord.com/api/webhooks/<id>/<token>'

cd efk && ./install.sh
```

> La password de `elastic` la genera ECK automáticamente — no se commitea.
> Recuperarla con: `kubectl -n elastic get secret scraper-es-elastic-user -o jsonpath='{.data.elastic}' | base64 -d`

Ver [`TP2/P2/README.md`](TP2/P2/README.md) o [`efk/README.md`](efk/README.md) para detalles completos.

## Namespaces

| Namespace | Stack | Acceso |
|---|---|---|
| `observability` | Loki + Promtail + Grafana (Parte 1) | `http://<NODE_IP>:30000` |
| `elastic` | Elasticsearch + Fluent Bit + Kibana (Parte 2) | `https://<NODE_IP>:30001` |
| `elastic-system` | ECK Operator | — |
| `ml-scraper` | Scraper de MercadoLibre (fuente de logs) | — |

## Estructura del repo

```
├── TP1/                        ← Contenido del TP 1
├── TP2/
│   ├── P1/                     ← Documentación Parte 1 (Loki)
│   │   ├── HIT1/..HIT6/        ← Documentación de cada hit
│   │   └── README.md
│   └── P2/                     ← Documentación Parte 2 (EFK)
│       ├── HIT1/..HIT6/        ← Documentación de cada hit
│       └── README.md
├── observability/              ← Stack Loki (TP 2 · Parte 1)
│   ├── helm/                   ← Values de Loki, Promtail, Grafana
│   ├── manifests/              ← Secrets, alerting rules
│   ├── dashboards/             ← Dashboard JSON provisionado as-code
│   ├── queries/                ← LogQL cookbook
│   ├── screenshots/            ← Capturas de validación
│   ├── install.sh
│   └── README.md
├── efk/                        ← Stack EFK (TP 2 · Parte 2)
│   ├── helm/                   ← Values de Fluent Bit + ECK operator
│   ├── manifests/              ← elasticsearch.yaml, kibana.yaml, ILM policy
│   ├── dashboards/             ← Dashboard NDJSON provisionado as-code
│   ├── queries/                ← KQL cookbook
│   ├── screenshots/            ← Capturas de validación
│   ├── install.sh
│   └── README.md
└── docs/
    └── adr/                    ← Architecture Decision Records
```
