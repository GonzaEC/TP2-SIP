# TP1 SIP — HIT 5: Robustez, manejo de errores y módulo de selectores

Extensión del scraper de MercadoLibre Argentina que incorpora un sistema de reintentos con manejo
de errores granular y un módulo centralizado de selectores CSS/XPath.

---

## Objetivo

Hacer el scraper resistente a fallos transitorios (timeouts, elementos no encontrados, cambios de
layout) mediante:

- **Reintentos automáticos** por producto, con hasta 3 intentos ante cualquier excepción.
- **Manejo de errores parciales**: si un campo opcional no se encuentra, el ítem se incluye igual
  con `null` en ese campo en lugar de descartarlo.
- **Módulo `Selectors`**: todos los selectores CSS/XPath viven en una sola clase, lo que facilita
  actualizarlos ante cambios de la UI de MercadoLibre sin tocar la lógica de negocio.

---

## Estructura del proyecto

```
HIT5/
├── pom.xml
├── output/
│   ├── bicicleta_rodado_29.json
│   ├── iphone_16_pro_max.json
│   └── geforce_rtx_5090.json
├── screenshots/
│   ├── bicicleta_rodado_29_chrome.png
│   ├── bicicleta_rodado_29_firefox.png
│   ├── iphone_16_pro_max_chrome.png
│   ├── iphone_16_pro_max_firefox.png
│   ├── geforce_rtx_5090_chrome.png
│   └── geforce_rtx_5090_firefox.png
└── src/main/java/ar/edu/sip/
    ├── BrowserFactory.java
    ├── MercadoLibreScraper.java
    ├── ProductResult.java
    └── Selectors.java
```

---

## Novedades respecto a HIT 4

### Módulo `Selectors.java`

Todos los selectores CSS y XPath están centralizados en la clase `Selectors`. Esto resuelve el
problema de tener strings duplicados diseminados en el código. Ante un cambio de layout de
MercadoLibre se actualiza un único lugar.

```java
// Ejemplo de uso
List<WebElement> contenedores = driver.findElements(Selectors.CONTENEDOR_RESULTADOS);
WebElement link = c.findElement(Selectors.PRODUCT_LINK);
```

### Sistema de reintentos (`ejecutarConReintentos`)

El scraper reintenta automáticamente hasta `MAX_REINTENTOS = 3` veces ante cualquier excepción
durante el procesamiento de un producto. Si los 3 intentos fallan, registra el error y continúa
con el siguiente producto.

```java
while (intento <= MAX_REINTENTOS) {
    try {
        procesarProducto(driver, wait, producto, browser);
        return;
    } catch (Exception e) {
        // log + reintentar
    }
}
```

La sincronización entre reintentos se delega a los `WebDriverWait` existentes en cada llamada
(`esperarResultados`, `elementToBeClickable`, etc.), sin usar `Thread.sleep()`.

### Helpers `tryGetText` y `tryGetLong`

Extraen campos opcionales de cada contenedor de producto. Si el elemento no existe lanzan
`NoSuchElementException` internamente, que es capturada y devuelta como `null` en lugar de
propagar el error.

```java
pr.setPrecio(tryGetLong(c, Selectors.PRODUCT_PRICE));          // null si no hay precio
pr.setTiendaOficial(tryGetText(c, Selectors.PRODUCT_OFFICIAL_STORE, "por "));
```

---

## Nota sobre cantidad de resultados

Para "GeForce RTX 5090" MercadoLibre retorna 2 resultados al combinar los filtros "Nuevo" +
"Solo tiendas oficiales". Es una limitación del catálogo disponible, no del scraper: el código
extrae todos los contenedores visibles (`Math.min(CANT_RESULTADOS, contenedores.size())`).

---

## Ejecución

```bash
cd HIT5

# Chrome (por defecto)
mvn compile exec:java

# Firefox
mvn compile exec:java -Dbrowser=firefox

# Firefox via variable de entorno (PowerShell)
$env:BROWSER = "firefox"
mvn compile exec:java
```

---

## Diferencias con HIT 4

| | HIT 4 | HIT 5 |
|---|---|---|
| Selectores | Inline en el scraper | Módulo `Selectors.java` centralizado |
| Manejo de errores | `try/catch` básico por ítem | Reintentos por producto + helpers granulares |
| Campos opcionales | `try/catch` por campo | `tryGetText` / `tryGetLong` reutilizables |
| Thread.sleep | No | No (sincronización vía WebDriverWait) |
