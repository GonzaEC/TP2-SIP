# TP1 SIP — HIT 4: Extracción multi-producto y JSON

Generalización del scraper de MercadoLibre Argentina para procesar una lista de productos en una sola ejecución y persistir los resultados como JSON estructurado.

---

## Objetivo

Sobre la base del HIT 3 (filtros aplicados por DOM + screenshot), procesar los **tres productos del enunciado** y, por cada uno:

- Extraer los primeros **10 resultados filtrados**.
- Capturar 6 campos por ítem (ver más abajo).
- Guardar la salida como un array JSON por producto en `output/`.
- Mantener la captura de screenshot del HIT 3.

**Productos objetivo:**
- `bicicleta rodado 29`
- `iPhone 16 Pro Max`
- `GeForce RTX 5090`

---

## Estructura del proyecto

```
HIT4/
├── pom.xml
├── output/
│   ├── bicicleta_rodado_29.json
│   ├── iphone_16_pro_max.json
│   └── geforce_rtx_5090.json
├── screenshots/
│   └── <producto>_<browser>.png
└── src/main/java/ar/edu/sip/
    ├── BrowserFactory.java
    ├── MercadoLibreScraper.java
    └── ProductResult.java
```

---

## Campos extraídos por ítem

| Campo | Tipo | Notas |
|---|---|---|
| `titulo` | `String` | Título del producto |
| `precio` | `Long` | Valor numérico en ARS, sin símbolo `$` ni separadores |
| `link` | `String` | URL absoluta al detalle del producto |
| `tienda_oficial` | `String` o `null` | Nombre de la tienda oficial si existe |
| `envio_gratis` | `boolean` | `true` si aparece la leyenda "Envío gratis" |
| `cuotas_sin_interes` | `String` o `null` | Texto de la oferta de cuotas si existe |

Los nombres en JSON usan `snake_case` (`tienda_oficial`, `envio_gratis`, `cuotas_sin_interes`) gracias a las anotaciones `@JsonProperty` de Jackson en `ProductResult.java`.

---

## Flujo de ejecución

Por cada producto de la lista `PRODUCTOS`:

1. Navegar a MercadoLibre AR y buscar el producto.
2. Esperar resultados (`presenceOfElementLocated` sobre `li.ui-search-layout__item`).
3. Cerrar el banner de cookies si aparece.
4. Aplicar filtros: **Nuevo** + **Solo tiendas oficiales** + orden **Más relevantes** (heredado de HIT 3).
5. Recorrer los primeros 10 contenedores y extraer los 6 campos.
6. Serializar la lista a JSON con Jackson (`ObjectMapper` con `INDENT_OUTPUT`) y guardar en `output/<slug>.json`.
7. Capturar screenshot en `screenshots/<slug>_<browser>.png`.

---

## Decisiones de diseño

### POJO + Jackson para serialización

`ProductResult` es un POJO simple con getters/setters y anotaciones `@JsonProperty` para los campos cuyo nombre en Java difiere del JSON (`tiendaOficial` → `tienda_oficial`, etc.). Jackson serializa la lista entera con una sola llamada:

```java
new ObjectMapper()
    .enable(SerializationFeature.INDENT_OUTPUT)
    .writeValue(destino.toFile(), resultados);
```

### Precio como `Long` numérico

El texto del precio de MercadoLibre incluye separadores de miles (`1.250.000`). Se limpia con `replaceAll("[^0-9]", "")` antes de parsear. Si el campo es no numérico (`"Precio a consultar"`) queda como `null`.

### Sanitización del nombre de archivo

Los productos tienen mayúsculas, espacios y caracteres especiales (`iPhone 16 Pro Max`, `GeForce RTX 5090`). Se sanitizan a slug ASCII para los nombres de archivo:

```java
s.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("_+$", "");
```

Resultado: `iphone_16_pro_max`, `geforce_rtx_5090`.

### Reuso del `WebDriver` entre productos

Se abre **una sola instancia** del navegador y se reutiliza para los 3 productos. El `try/finally` garantiza el `quit()` aunque uno falle. Esto evita levantar Chrome/Firefox 3 veces y reduce el tiempo total de ejecución.

---

## Ejecución

```bash
cd HIT4

# Chrome (por defecto)
mvn compile exec:java

# Firefox
mvn compile exec:java -Dbrowser=firefox

# Firefox via variable de entorno (PowerShell)
$env:BROWSER = "firefox"
mvn compile exec:java
```

### Salida esperada

```
[INFO] Procesando: bicicleta rodado 29
[INFO] JSON guardado en: output/bicicleta_rodado_29.json
[INFO] Procesando: iPhone 16 Pro Max
[INFO] JSON guardado en: output/iphone_16_pro_max.json
[INFO] Procesando: GeForce RTX 5090
[INFO] JSON guardado en: output/geforce_rtx_5090.json
```

### Ejemplo de un ítem en el JSON

```json
{
  "titulo": "iPhone 16 Pro Max 256gb",
  "precio": 2890000,
  "link": "https://articulo.mercadolibre.com.ar/MLA-...",
  "tienda_oficial": "Apple",
  "envio_gratis": true,
  "cuotas_sin_interes": "12x $240.833 sin interés"
}
```

---

## Diferencias con HIT 3

| | HIT 3 | HIT 4 |
|---|---|---|
| Productos por ejecución | 1 (`bicicleta rodado 29`) | 3 (lista parametrizable) |
| Salida | Solo screenshot + stdout | Screenshot + JSON estructurado |
| Campos extraídos | Título (top 5) | 6 campos × 10 ítems × 3 productos |
| Dependencias | Solo Selenium | Selenium + Jackson |
