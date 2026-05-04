# TP1 SIP — HIT 3: Filtros por DOM y Screenshot

Extensión del scraper de MercadoLibre Argentina que aplica filtros reales navegando el DOM (sin modificar la URL manualmente) y captura un screenshot de la página de resultados filtrada.

---

## Objetivo

Luego de buscar **"bicicleta rodado 29"**, aplicar los siguientes filtros interactuando con el DOM como lo haría un usuario real:

- **Condición:** Nuevo
- **Tienda:** Solo tiendas oficiales
- **Orden:** Más relevantes

Capturar un screenshot de la página resultante y guardarlo como `screenshots/<producto>_<browser>.png`.

---

## Estructura del proyecto

```
HIT3/
├── pom.xml
├── screenshots/
│   └── bicicleta_rodado_29_chrome.png
└── src/main/java/ar/edu/sip/
    ├── BrowserFactory.java
    └── MercadoLibreScraper.java
```

---

## Flujo de ejecución

1. Se abre el navegador y se busca el producto.
2. Se espera a que carguen los ítems de resultado (`li.ui-search-layout__item`).
3. Se cierra el banner de cookies si está presente.
4. Se aplica el filtro **"Nuevo"**.
5. Se aplica el filtro **"Solo tiendas oficiales"**.
6. Se aplica el orden **"Más relevantes"** desde el dropdown.
7. Se captura el screenshot y se guarda en `screenshots/`.
8. Se imprimen los primeros 5 títulos filtrados.

---

## Decisiones de diseño

### Interacción real con el DOM

Los filtros se aplican haciendo click sobre los enlaces del panel lateral, exactamente como lo haría un usuario. No se modifica la URL a mano. Esto valida el flujo completo de navegación.

### JS click en lugar de click nativo

Selenium lanza `ElementClickInterceptedException` cuando otro elemento cubre al objetivo en el momento del click (overlays, banners, headers sticky). Para evitarlo, se usa `JavascriptExecutor.executeScript("arguments[0].click()")` sobre el elemento ya localizado. El elemento se localiza igual con `presenceOfElementLocated`, manteniendo la espera explícita; solo el disparo del click es vía JS.

### Scroll antes del click

Antes de cada click se llama a `scrollIntoView({block:'center'})` para asegurarse de que el elemento esté visible en el viewport y no quede tapado por el header sticky de la página.

### Cierre del banner de cookies

MercadoLibre muestra un banner de consentimiento de cookies que cubre parte del panel de filtros. Se cierra automáticamente al inicio con un `WebDriverWait` corto (5 s) para no penalizar el tiempo total cuando el banner no aparece.

### Texto exacto del filtro

El texto clickeable real del filtro de tiendas es **"Solo tiendas oficiales"** (no "Tienda oficial"). Se detectó inspeccionando el DOM con las DevTools del navegador mientras corría el script.

### Dropdown de orden (Andes UI)

El selector de orden de MercadoLibre usa el componente Andes con un `<ul role="listbox">` flotante. El error original (`ElementClickInterceptedException`) ocurría porque el XPath apuntaba al `<span>` interior del ítem en lugar del `<li role="option">` contenedor. Se corrigió el XPath para apuntar al `<li>` y se usa JS click para evitar la intercepción del overlay del listbox.

### Screenshot

Se usa la interfaz `TakesScreenshot` de Selenium. El directorio `screenshots/` se crea automáticamente con `Files.createDirectories()` si no existe. El nombre del archivo sanitiza el producto (espacios y caracteres especiales reemplazados por `_`) y agrega el nombre del navegador: `bicicleta_rodado_29_chrome.png`.

---

## Ejecución

```bash
cd HIT3

# Chrome (por defecto)
mvn compile exec:java

# Firefox
mvn compile exec:java -Dbrowser=firefox
```

### Salida esperada

```
[INFO] Banner de cookies cerrado.
[INFO] Filtro aplicado: Nuevo
[INFO] Filtro aplicado: Solo tiendas oficiales
[INFO] Orden aplicado: Más relevantes
[INFO] Screenshot guardado en: ...\HIT3\screenshots\bicicleta_rodado_29_chrome.png
[INFO] Selector de títulos utilizado: a.poly-component__title

=== Primeros 5 resultados filtrados para: "bicicleta rodado 29" ===

  1. Bicicleta Alpina Mtb Mountain Bike 1.0 Rodado 29 ...
  2. Mountain Bike Fierce F210 Acero 21 Velocidades ...
  3. Bicicleta Mtb Alpina 3.0 Cuadro Aluminio Rod.29 ...
  4. ...
  5. ...
```

---

## Diferencia con HIT 1 y HIT 2

| | HIT 1 | HIT 2 | HIT 3 |
|---|---|---|---|
| Navegador | Solo Chrome | Chrome / Firefox | Chrome / Firefox |
| Filtros | Ninguno | Ninguno | Nuevo + Solo tiendas oficiales + Más relevantes |
| Screenshot | No | No | Sí |
| Cookie banner | No manejado | No manejado | Cerrado automáticamente |
| Tipo de click | Nativo | Nativo | JS click + scrollIntoView |
