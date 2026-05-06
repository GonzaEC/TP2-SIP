# TP2-SIP

Trabajo Práctico Nº 2 — Seminario de Integración Profesional 2026

## TP 2 · Parte 1 — Observabilidad con Loki

Logging centralizado del scraper en k3s con Loki + Promtail + Grafana.

### Levantar el stack

```bash
export GRAFANA_ADMIN_PASSWORD='<tu-password-segura>'
cd observability && ./install.sh
```

Ver [observability/README.md](observability/README.md) para detalles completos.

## TP 2 · Parte 2 — Logging centralizado con EFK

Stack EFK (Elasticsearch + Fluent Bit + Kibana) corriendo en paralelo con el stack Loki de Parte 1.

### Levantar el stack

```bash
cd efk && ./install.sh
```

> La password de `elastic` la genera ECK automáticamente — no se commitea.
> `DISCORD_WEBHOOK_URL` es opcional (Hit #6 bonus):
> ```bash
> export DISCORD_WEBHOOK_URL='https://discord.com/api/webhooks/...'
> cd efk && ./install.sh
> ```

Ver [efk/README.md](efk/README.md) para detalles completos.

## Estructura del repo

```
├── TP1/                    ← Contenido del TP 1
├── TP2/
│   └── P1/
│       ├── HIT1/..HIT6/   ← Documentación de cada hit
│       └── P2/
├── observability/          ← Stack de observabilidad (TP 2 · Parte 1 — Loki)
│   ├── helm/
│   ├── manifests/
│   ├── dashboards/
│   ├── queries/
│   ├── install.sh
│   └── README.md
├── efk/                    ← Stack de logging (TP 2 · Parte 2 — EFK)
│   ├── helm/
│   ├── manifests/
│   ├── dashboards/
│   ├── queries/
│   ├── screenshots/
│   ├── install.sh
│   └── README.md
└── docs/
    └── adr/                ← Architecture Decision Records
```
