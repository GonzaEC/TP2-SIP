// HIT6/src/test/java/ar/edu/sip/ProductResultSchemaTest.java
package ar.edu.sip;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Valida el schema minimo, precios positivos y links absolutos sobre los JSON generados por el
 * scraper (o sobre datos sinteticos en unit).
 */
class ProductResultSchemaTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  // ── Helpers de fabrica ───────────────────────────────────────────────────

  private static ProductResult buildValid(String titulo, long precio, String link) {
    ProductResult p = new ProductResult();

    p.setTitulo(titulo);
    p.setPrecio(precio);
    p.setLink(link);
    p.setEnvioGratis(false);

    return p;
  }

  // ── Schema: campos requeridos y tipos ────────────────────────────────────

  @Test
  void schemaTituloPrecioLinkNoSonNulos() {
    ProductResult p =
        buildValid(
            "Bicicleta MTB 29", 250_000L, "https://articulo.mercadolibre.com.ar/MLA-123456789");
    assertNotNull(p.getTitulo(), "titulo no debe ser null");
    assertNotNull(p.getPrecio(), "precio no debe ser null");
    assertNotNull(p.getLink(), "link no debe ser null");
  }

  @Test
  void schemaTituloEsString() {
    ProductResult p = buildValid("Test", 1L, "https://example.com");
    assertInstanceOf(String.class, p.getTitulo());
  }

  @Test
  void schemaPrecioEsLong() {
    ProductResult p = buildValid("Test", 1L, "https://example.com");
    assertInstanceOf(Long.class, p.getPrecio());
  }

  @Test
  void schemaEnvioGratisEsBoolean() {
    ProductResult p = buildValid("Test", 1L, "https://example.com");
    // boolean primitivo → la comparacion ya garantiza el tipo
    assertFalse(p.isEnvioGratis());
  }

  @Test
  void schemaSerializaYDeserializaCorrectamente() throws IOException {
    ProductResult original =
        buildValid("RTX 5090", 3_500_000L, "https://articulo.mercadolibre.com.ar/MLA-9999");

    original.setTiendaOficial("NVIDIA Store");
    original.setEnvioGratis(true);
    original.setCuotasSinInteres("12x sin interés");

    String json = MAPPER.writeValueAsString(original);

    ProductResult leido = MAPPER.readValue(json, ProductResult.class);

    assertEquals(original.getTitulo(), leido.getTitulo());
    assertEquals(original.getPrecio(), leido.getPrecio());
    assertEquals(original.getLink(), leido.getLink());
    assertEquals(original.getTiendaOficial(), leido.getTiendaOficial());
    assertEquals(original.isEnvioGratis(), leido.isEnvioGratis());
    assertEquals(original.getCuotasSinInteres(), leido.getCuotasSinInteres());
  }

  @Test
  void schemaCamposOpcionalesPuedenSerNulos() throws IOException {
    ProductResult p = buildValid("Solo titulo", 100_000L, "https://example.com");

    // tienda_oficial y cuotas_sin_interes son null → no deben romper la serializacion
    String json = MAPPER.writeValueAsString(p);

    assertNotNull(json);

    // El campo cuotas_sin_interes null debe serializarse como null o ausentarse
    ProductResult leido = MAPPER.readValue(json, ProductResult.class);

    assertNull(leido.getTiendaOficial());
    assertNull(leido.getCuotasSinInteres());
  }

  // ── Precios positivos ────────────────────────────────────────────────────

  @ParameterizedTest
  @ValueSource(longs = {1L, 100L, 250_000L, 3_500_000L, Long.MAX_VALUE})
  void precioValoresPositivosSonValidos(long precio) {
    ProductResult p = buildValid("X", precio, "https://example.com");
    assertTrue(p.getPrecio() > 0, "El precio debe ser positivo: " + precio);
  }

  @ParameterizedTest
  @ValueSource(longs = {0L, -1L, -999_999L})
  void precioValoresCeroONegativoNoSonValidos(long precio) {
    // El scraper no deberia generar estos valores; este test documenta la invariante
    assertFalse(precio > 0, "Precio " + precio + " no es positivo");
  }

  @Test
  void precioNuloEsAceptableParaItemsSinPrecioVisible() {
    ProductResult p = new ProductResult();
    p.setTitulo("Producto sin precio");
    p.setLink("https://example.com");
    assertNull(p.getPrecio()); // Campo opcional
  }

  // ── Links absolutos ──────────────────────────────────────────────────────

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://articulo.mercadolibre.com.ar/MLA-123456789",
        "https://www.mercadolibre.com.ar/p/MLA12345678",
        "http://articulo.mercadolibre.com.ar/MLA-000000001"
      })
  void linkUrlAbsolutaValidaEsAceptada(String url) {
    assertDoesNotThrow(
        () -> {
          URI uri = new URI(url);
          assertTrue(uri.isAbsolute(), "La URI debe ser absoluta: " + url);
          assertNotNull(uri.getScheme());
          assertNotNull(uri.getHost());
        });
  }

  @ParameterizedTest
  @ValueSource(strings = {"/MLA-123456789", "articulo.mercadolibre.com.ar/MLA-1", ""})
  void linkUrlRelativaOVaciaNoEsAbsoluta(String url) throws Exception {
    if (url.isEmpty()) {
      assertTrue(url.isEmpty());
      return;
    }
    URI uri = new URI(url);
    assertFalse(uri.isAbsolute(), "Se esperaba URL relativa: " + url);
  }

  // ── Validacion sobre archivos JSON generados (si existen) ────────────────

  @Test
  void jsonGeneradoSiExisteCumpleSchemaMinimo() throws IOException {
    Path outputDir = Path.of("output");
    if (!Files.exists(outputDir)) {
      // El directorio no existe aun (entorno de test limpio); se omite
      return;
    }

    try (Stream<Path> files = Files.list(outputDir)) {
      List<Path> jsons = files.filter(p -> p.toString().endsWith(".json")).toList();

      for (Path jsonFile : jsons) {
        ProductResult[] items = MAPPER.readValue(jsonFile.toFile(), ProductResult[].class);
        assertNotNull(items, "El array no debe ser null: " + jsonFile);

        for (ProductResult item : items) {
          assertNotNull(item.getTitulo(), "titulo null en " + jsonFile.getFileName());
          assertFalse(item.getTitulo().isBlank(), "titulo vacio en " + jsonFile.getFileName());
          assertNotNull(item.getLink(), "link null en " + jsonFile.getFileName());

          // Precio: si presente debe ser positivo
          if (item.getPrecio() != null) {
            assertTrue(item.getPrecio() > 0, "precio no positivo en " + jsonFile.getFileName());
          }

          // Link: debe ser URL absoluta
          URI uri =
              assertDoesNotThrow(
                  () -> new URI(item.getLink()),
                  "link no es URI valida en " + jsonFile.getFileName());
          assertTrue(uri.isAbsolute(), "link no es absoluta en " + jsonFile.getFileName());
        }
      }
    }
  }
}
