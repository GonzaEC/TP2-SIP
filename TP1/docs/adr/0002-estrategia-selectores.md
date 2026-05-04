# ADR-0002: Selectores CSS semánticos con fallbacks múltiples, centralizados en módulo aparte

- **Estado:** Aceptado
- **Fecha:** 2026-03-20
- **Autores:** Equipo TP1-SIP

## Contexto

MercadoLibre Argentina rota su frontend de búsqueda con regularidad: convive el layout viejo (`ui-search-*`) con el nuevo (`poly-component__*`), y ningún elemento expone atributos `data-test-id` estables. XPath posicional (`//div[3]/a`) es frágil ante cualquier reordenamiento. Necesitamos selectores que sobrevivan al menos un sprint sin tocar el scraper, y que cuando rompan se arreglen en un solo lugar (Hit #5 lo exige explícitamente).

## Decisión

Usar **selectores CSS basados en clases semánticas**, agrupando múltiples variantes con coma (fallback OR) en cada constante, y centralizar todo en `Selectors.java`. Solo recurrir a XPath cuando el target depende del texto visible (filtros laterales, opciones de orden), nunca para posición.

Ejemplo del patrón aplicado en [Selectors.java:18-23](HIT6/src/main/java/ar/edu/sip/Selectors.java):
```java
public static final By PRODUCT_LINK = By.cssSelector(
    "a.poly-component__title,"          // layout nuevo
  + " .ui-search-item__title,"          // layout viejo
  + " .ui-search-link__title-card,"
  + " .ui-search-item__group__element.ui-search-link");
```

## Consecuencias

- **Positivas:**
  - Tolerancia a layouts mixtos: el mismo scraper funciona contra ambos sin ramas condicionales.
  - Cambios de DOM se arreglan en un solo archivo (`Selectors.java`), no esparcidos por la lógica de scraping.
  - Los nombres de constantes (`PRODUCT_PRICE`, `BANNER_COOKIES`) documentan intención.
- **Negativas:**
  - Si MercadoLibre introduce un tercer layout, hay que editar varios selectores a la vez.
  - Selectores con muchos fallbacks pueden enmascarar regresiones silenciosas (un selector viejo seguirá matcheando aunque el nuevo se rompa).
- **Neutrales:**
  - Los tests unitarios mockean `WebElement` directamente, así que no validan que los selectores reales sigan resolviendo. Eso queda cubierto por los E2E tests (`ScraperE2ETest`).

## Alternativas consideradas

- **`data-test-id` / `data-qa`:** sería ideal, pero ML no los expone públicamente. Fuera de control del equipo.
- **XPath posicional (`//li[3]//a`):** rompe ante cualquier reordenamiento; descartado de entrada.
- **XPath por texto (`//a[contains(., 'Producto')]`):** se reservó para filtros y opciones de orden, donde el texto **es** el contrato (cambia con i18n pero es estable). No para extracción de campos.
