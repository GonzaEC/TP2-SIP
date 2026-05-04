# TP1 SIP — HIT 1: Scraper básico con Chrome

Scraper de MercadoLibre Argentina construido con Java 17 y Selenium WebDriver 4 que busca productos y extrae los primeros 5 títulos de los resultados.

---

## Objetivo

Abrir Chrome, navegar a MercadoLibre AR, buscar **"bicicleta rodado 29"** y mostrar los títulos de los primeros 5 resultados usando únicamente explicit waits. Está prohibido el uso de `Thread.sleep()` como mecanismo de sincronización.

---

## Estructura del proyecto

```
HIT1/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── ar/edu/sip/
                └── MercadoLibreScraper.java
```

---

## Dependencias

El proyecto usa **Maven** como sistema de build. La única dependencia es `selenium-java 4.20.0`, que incluye internamente **Selenium Manager**: un binario que detecta la versión del navegador instalado y descarga el driver correspondiente (`chromedriver`) de forma automática, sin configuración manual.

```xml
<dependency>
    <groupId>org.seleniumhq.selenium</groupId>
    <artifactId>selenium-java</artifactId>
    <version>4.20.0</version>
</dependency>
```

---

## Flujo de ejecución

1. Se crea una instancia de `ChromeDriver` con opciones para suprimir el banner de automatización.
2. Se navega a `https://www.mercadolibre.com.ar`.
3. Se espera con `WebDriverWait` a que el campo de búsqueda sea interactuable (`elementToBeClickable`).
4. Se escribe el término de búsqueda y se envía con `Keys.ENTER`.
5. Se espera a que aparezca al menos un `li.ui-search-layout__item` en el DOM (`presenceOfElementLocated`), confirmando que la página de resultados cargó.
6. Se recorren selectores CSS candidatos en orden hasta encontrar uno que devuelva elementos con texto.
7. Se imprimen los primeros 5 títulos por consola.

---

## Decisiones de diseño

### Sincronización sin `Thread.sleep()`

Toda la sincronización se realiza con `WebDriverWait` + `ExpectedConditions`. Se espera la presencia de `li.ui-search-layout__item` en el DOM en lugar de verificar la URL, porque MercadoLibre AR redirige a `listado.mercadolibre.com.ar/<slug>` (no a un path `/search?`). Esperar un elemento concreto del DOM es más robusto que depender del formato de la URL.

### Selectores CSS con fallback

MercadoLibre cambia su estructura HTML con frecuencia. Para hacer el scraper tolerante a esos cambios, se define una lista ordenada de selectores candidatos:

```java
private static final String[] SELECTORES_TITULO = {
    "a.poly-component__title",
    "h2.poly-box a",
    ".ui-search-item__title",
    "li.ui-search-layout__item h2 a",
    "a.ui-search-link__title-card"
};
```

El código prueba cada uno y usa el primero que devuelva elementos con texto no vacío. Si ninguno funciona, se imprime la URL actual y el título de la página para facilitar el diagnóstico.

---

## Ejecución

```bash
cd HIT1
mvn compile exec:java
```

### Salida esperada

```
[INFO] Selector utilizado: a.poly-component__title

=== Primeros 5 resultados para: "bicicleta rodado 29" ===

  1. Bicicleta Mtb Acero Jeico F1 R29 21v Talle M Rosa
  2. Bicicleta Mountain Bike Rodado 29 Fire Bird Turbo ...
  3. Bicicleta Mtb Luxus Mboi Rodado 29 21vel Shimano ...
  4. Bicicleta Slp Mtb 25 Pro Aluminio Rodado 29 Shimano 21v
  5. Bicicleta Alpina Mtb Mountain Bike 1.0 Rodado 29 ...
```

---

## Advertencia conocida

```
Unable to find CDP implementation matching 147
```

Este warning aparece porque Selenium 4.20.0 no incluye el módulo CDP para Chrome 147 (versión muy reciente al momento del desarrollo). No afecta al scraping: el código usa únicamente el protocolo W3C WebDriver estándar y no depende de CDP en ningún punto.
