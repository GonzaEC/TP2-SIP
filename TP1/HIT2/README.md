# TP1 SIP — HIT 2: Selenium WebDriver Scraper

## HIT 2 — Browser Factory (Chrome y Firefox)

### Objetivo

Refactorizar el scraper para introducir una `BrowserFactory` que permita elegir el navegador desde la línea de comandos o una variable de entorno, sin modificar el código fuente.

### Resolución

Se extrae la creación del driver a una clase `BrowserFactory` con un único método público `create()`. La elección del navegador sigue esta cadena de prioridad:

```
argumento directo → propiedad -Dbrowser → variable $BROWSER → "chrome" (default)
```

Esto permite tres modos de uso:

| Modo | Comando |
|------|---------|
| Interactivo (Maven) | `mvn compile exec:java -Dbrowser=firefox` |
| CI/CD (variable de entorno) | `$env:BROWSER = "firefox"; mvn compile exec:java` |
| Programático | `BrowserFactory.create("firefox")` |

El `pom.xml` mapea la propiedad Maven `browser` a una system property de la JVM usando la sección `<systemProperties>` del `exec-maven-plugin`. Esto evita problemas de parsing en PowerShell con argumentos `-D` que contienen puntos.

### Ejecución

```bash
cd HIT2

# Chrome (por defecto)
mvn compile exec:java

# Firefox
mvn compile exec:java -Dbrowser=firefox

# Firefox via variable de entorno (PowerShell)
$env:BROWSER = "firefox"
mvn compile exec:java
```

---

## Diferencias entre Chrome y Firefox

### Warning de CDP

Al inicializar `ChromeDriver`, Selenium intenta negociar la versión del **Chrome DevTools Protocol (CDP)** con el navegador. Si la versión instalada es más nueva que la cubierta por Selenium, aparece el siguiente warning:

```
Unable to find CDP implementation matching 147
```

Firefox no genera este warning porque no implementa CDP. Usa **WebDriver BiDi**, un protocolo distinto que Selenium maneja por separado. El warning de Chrome es cosmético: no afecta al scraping porque el código solo usa el protocolo W3C WebDriver estándar, compatible con ambos navegadores.

### Opciones de configuración del driver

Las opciones para suprimir la detección de automatización son distintas en cada navegador:

| | Chrome | Firefox |
|---|---|---|
| Clase de opciones | `ChromeOptions` | `FirefoxOptions` |
| Suprimir banner de automatización | `excludeSwitches("enable-automation")` | No aplica |
| Suprimir detección WebDriver | `--disable-blink-features=AutomationControlled` | `dom.webdriver.enabled = false` (preference) |

`excludeSwitches` es una API exclusiva de Chromium y lanzaría una excepción si se intentara usar en Firefox.

### Selectores CSS

Los selectores funcionan de forma idéntica en ambos navegadores. MercadoLibre devuelve el mismo HTML independientemente del user-agent, por lo que `a.poly-component__title` resuelve correctamente en Chrome y Firefox.

### Tiempos de carga

Firefox tiende a tardar más en la inicialización del driver (descarga de `geckodriver` en el primer uso, arranque del proceso) y puede requerir un timeout levemente mayor en conexiones lentas. El timeout de 20 segundos configurado es suficiente para ambos en condiciones normales.

### Hilos residuales con `exec:java`

Ambos navegadores dejan hilos internos de Selenium activos al terminar. Como `exec:java` comparte la JVM con Maven, esos hilos no se terminan limpiamente. Se resuelve con `System.exit(0)` al final del `main`, que fuerza el cierre de la JVM y elimina el warning de Maven.
