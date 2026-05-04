# TP1 SIP — HIT 6: Headless, tests automatizados, Docker y CI

Endurecimiento del scraper de MercadoLibre Argentina con modo headless controlable por variable de entorno, suite de tests JUnit 5 + Mockito con cobertura ≥ 70 % (JaCoCo), empaquetado en imagen Docker multi-stage con Chrome y Firefox, y pipeline de GitHub Actions completo.

> **Nota:** La documentación detallada del proyecto completo (estructura, stack, principios de implementación, comandos de Docker, CI, pre-commit, ADRs) vive en el [README raíz](../README.md). Este README solo enumera lo que agrega HIT 6 sobre los hits anteriores.

---

## Lo nuevo en este hit

### Modo headless por variable de entorno

**Linux / macOS:**
```bash
# Headless (sin abrir ventana)
HEADLESS=true mvn exec:java

# Visible (debug)
HEADLESS=false mvn exec:java
```

**Windows (PowerShell):**
```powershell
# Headless (sin abrir ventana)
$env:HEADLESS="true"; mvn exec:java

# Visible (debug)
$env:HEADLESS="false"; mvn exec:java
```

Cadena de resolución: `-Dheadless` → `$HEADLESS` → `false` (default).

### Tests automatizados

Suite completa con JUnit 5 + Mockito. **No abren browser real** (los `WebDriver` se mockean), corren en milisegundos:

| Archivo | Qué valida |
|---|---|
| `BrowserFactoryTest` | Resolución de browser y headless por property/env, fallback a default, instanciación de drivers |
| `MercadoLibreScrapperTest` | Los 4 criterios del hit: ≥10 resultados, schema, precios positivos, links absolutos. También `tryGetText`, `tryGetLong`, `sanitizar`, retries, banner, filtros, orden |
| `ProductResultSchemaTest` | Schema mínimo del JSON, anotaciones `@JsonProperty`, tipos correctos |

```bash
mvn test                  # solo tests
mvn verify                # tests + JaCoCo + check ≥70%
```

Reporte HTML de cobertura en `target/site/jacoco/index.html`.

### Docker multi-stage

| Stage | Qué hace | Tamaño aprox. |
|---|---|---|
| `builder` | Compila con Maven, genera fat jar (shaded) con `Main-Class` declarado | descartado |
| `runtime` | JRE 17 + Chrome stable (repo APT oficial Google) + Firefox (repo APT oficial Mozilla) | ~1 GB |

Drivers (`chromedriver`, `geckodriver`) los resuelve **Selenium Manager** en runtime contra la versión real instalada — no hay version-matching que mantener.

Comandos están en el [README raíz](../README.md#docker).

### Docker Compose

Tres servicios:
- `scraper` — corre el scraper headless contra Chrome/Firefox según `BROWSER`
- `lint` — ejecuta `checkstyle:check` + `spotless:check` en un container (no requiere Maven local)
- `test` — corre `mvn verify` con JaCoCo en un container

Ver `docker-compose.yml` para la config completa.

### Logging estructurado (SLF4J + Logback)

Reemplaza los `System.out/err.println` por logs con nivel, timestamp y nombre de clase.

Configuración en `src/main/resources/logback.xml`:
- **ConsoleAppender** — imprime en stdout con el patrón `HH:mm:ss.SSS LEVEL [logger] mensaje`
- **RollingFileAppender** — escribe en `logs/scraper.log`, rota por tamaño (2 MB) y tiempo; conserva 3 archivos históricos (10 MB totales máximo)

Ejemplo de salida:
```
21:30:15.123 INFO  [a.e.s.BrowserFactory] Browser: chrome | Headless: true
21:30:16.456 INFO  [a.e.s.MercadoLibreScraper] Iniciando: bicicleta rodado 29
21:31:05.789 WARN  [a.e.s.MercadoLibreScraper] Filtro 'Solo tiendas oficiales' no aplicado en 'bicicleta rodado 29': ...
21:31:26.789 INFO  [a.e.s.MercadoLibreScraper] JSON guardado: /app/output/bicicleta_rodado_29.json
```

### Pipeline CI

`.github/workflows/scrape.yml` con 3 jobs en cadena:

```
secrets-scan (gitleaks)
    └── unit-tests (chrome) ──┐
    └── unit-tests (firefox) ─┴── docker-scraper (chrome)
                                  docker-scraper (firefox)
```

Detalles en el [README raíz](../README.md#pipeline-ci-github-actions).

---

## Diferencias con HIT 5

| | HIT 5 | HIT 6 |
|---|---|---|
| Modo headless | Hardcoded `false` | Variable de entorno (`HEADLESS=true`) |
| Tests | Ninguno | JUnit 5 + Mockito, cobertura ≥ 70 % |
| Empaquetado | `mvn exec:java` local | Imagen Docker multi-stage con browsers incluidos |
| Orquestación | Manual | `docker-compose` (scraper, lint, test) |
| CI | No | GitHub Actions con matriz Chrome/Firefox + Gitleaks |
| Calidad de código | Selectors centralizado | + Spotless + Checkstyle + pre-commit hooks |
| Logging | `System.out.println` | SLF4J + Logback con niveles, timestamp y rotación de archivo |

---

## Ejecución rápida

**Linux / macOS:**
```bash
cd HIT6

# Local con Chrome headless
HEADLESS=true mvn exec:java

# Tests + cobertura
mvn verify

# Empaquetado y ejecución en Docker
docker compose up scraper                  # Chrome
BROWSER=firefox docker compose up scraper  # Firefox

# Lint en container
docker compose run --rm lint
```

**Windows (PowerShell):**
```powershell
cd HIT6

# Local con Chrome headless
$env:HEADLESS="true"; mvn exec:java

# Tests + cobertura
mvn verify

# Empaquetado y ejecución en Docker
docker compose up scraper                  # Chrome
$env:BROWSER="firefox"; docker compose up scraper  # Firefox

# Lint en container
docker compose run --rm lint
```
