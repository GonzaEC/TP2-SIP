# TP1 SIP — HIT 8: Capacidad Extendida

Extiende el scraper del [HIT 6](../HIT6/README.md) con tres mejoras:

1. **Paginación** — navega hasta 3 páginas recolectando 30 resultados por producto.
2. **Estadísticas de precio** — calcula y muestra mín / máx / mediana / desvío estándar.
3. **Histórico en PostgreSQL** — persiste resultados en un StatefulSet de k3s vía Flyway.

---

## Nuevas clases

| Clase | Responsabilidad |
|---|---|
| `PriceStats` | Calcula mín, máx, mediana y σ de los precios de una lista de resultados |
| `PostgresWriter` | Aplica migraciones Flyway y persiste resultados en `scrape_results` y `price_stats` |

El resto de clases (`MercadoLibreScraper`, `BrowserFactory`, `Selectors`, `ProductResult`) son extensiones de HIT 6.

---

## Ejecutar localmente

```bash
cd HIT8
mvn verify             # tests + cobertura (>70%)
mvn exec:java          # scraper real (abre browser)
```

Para que se guarden resultados en BD local, exportar antes:

```bash
export POSTGRES_HOST=localhost
export POSTGRES_USER=scraper
export POSTGRES_PASSWORD=scraper123
export POSTGRES_DB=scraper
```

---

## Estructura `HIT8/k8s/`

| Archivo | Qué despliega |
|---|---|
| `postgres-secret.yaml` | Credenciales de PostgreSQL (Secret) |
| `postgres-pvc.yaml` | Volumen persistente 1 GB para datos de PostgreSQL |
| `postgres-statefulset.yaml` | StatefulSet de PostgreSQL 16 Alpine |
| `postgres-service.yaml` | Service ClusterIP `postgres:5432` |
| `configmap.yaml` | Variables de entorno del scraper (incluye `POSTGRES_HOST`) |
| `pvc.yaml` | Volumen persistente para JSONs y screenshots del scraper |
| `job.yaml` | Job one-shot con initContainer que espera a PostgreSQL |
| `cronjob.yaml` | CronJob cada hora con el mismo patrón |

---

## Despliegue en k3d

```bash
# 1. Cluster (si no existe)
k3d cluster create scraper

# 2. Aplicar todos los manifiestos
kubectl apply -f HIT8/k8s/

# 3. Verificar que PostgreSQL está listo
kubectl get pods -l app=postgres
kubectl logs -l app=postgres

# 4. Ver estado del Job
kubectl get pods -l job-name=scraper-once
kubectl logs -l job-name=scraper-once -f
```

Salida esperada del scraper (formato SLF4J + Logback):

```
21:30:15.123 INFO  [a.e.s.BrowserFactory     ] Browser: chrome | Headless: true
21:30:16.456 INFO  [a.e.s.MercadoLibreScraper] Iniciando: bicicleta rodado 29
21:31:05.001 INFO  [a.e.s.MercadoLibreScraper] Página 1 — 10 ítems extraídos (acumulado: 10/30)
21:31:25.234 INFO  [a.e.s.MercadoLibreScraper] Página 2 — 10 ítems extraídos (acumulado: 20/30)
21:31:45.567 INFO  [a.e.s.MercadoLibreScraper] Página 3 — 10 ítems extraídos (acumulado: 30/30)
21:31:46.890 INFO  [a.e.s.MercadoLibreScraper] Stats: bicicleta rodado 29
21:31:46.891 INFO  [ar.edu.sip.PriceStats    ]   bicicleta rodado 29            | min=120.000 | max=980.000 | mediana=320.000 | σ=210.000 | n=28
21:31:47.123 INFO  [a.e.s.MercadoLibreScraper] JSON guardado: /app/output/bicicleta_rodado_29.json
21:31:47.456 INFO  [ar.edu.sip.PostgresWriter] Guardados 30 resultados para 'bicicleta rodado 29'
```

---

## Verificar datos en PostgreSQL

```bash
# Port-forward al pod de PostgreSQL
kubectl port-forward svc/postgres 5432:5432

# En otra terminal
psql -h localhost -U scraper -d scraper -c "SELECT COUNT(*) FROM scrape_results;"
psql -h localhost -U scraper -d scraper -c "SELECT * FROM price_stats ORDER BY scraped_at DESC;"
```

---

## Migración Flyway

El archivo `src/main/resources/db/migration/V1__create_scrape_results.sql` crea las tablas al primer arranque. Flyway lleva el historial en la tabla `flyway_schema_history`.

---

## Limpieza

```bash
kubectl delete -f HIT8/k8s/
```

---

## Logging estructurado

El scraper usa **SLF4J + Logback** (configurado en `src/main/resources/logback.xml`):

| Appender | Destino | Rotación |
|---|---|---|
| `CONSOLE` | stdout | — |
| `FILE` | `logs/scraper.log` | 2 MB por archivo, 3 días de historial, 10 MB totales |

Todos los niveles (INFO / WARN / ERROR) llevan timestamp, nivel y nombre abreviado del logger, lo que permite filtrar en CI con `grep ERROR` o configurar alertas en Kubernetes.

---

## Notas de diseño

- `POSTGRES_PASSWORD` viene del Secret; las demás variables de entorno del ConfigMap.
- El initContainer `wait-for-postgres` usa `nc -z` para esperar que el puerto 5432 esté disponible antes de arrancar el scraper.
- Si `POSTGRES_HOST` no está configurado, el scraper sigue funcionando (guarda JSON pero omite BD).
- La cobertura de `PostgresWriter` queda excluida del check de JaCoCo porque requiere una BD real.
