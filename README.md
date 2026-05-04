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

## Estructura del repo

```
├── TP1/                    ← Contenido del TP 1
├── TP2/
│   └── P1/
│       ├── HIT1/..HIT6/   ← Documentación de cada hit
│       └── P2/
└── observability/          ← Stack de observabilidad (TP 2 · Parte 1)
    ├── helm/
    ├── manifests/
    ├── dashboards/
    ├── queries/
    ├── install.sh
    └── README.md
```
