// HIT6/src/test/java/ar/edu/sip/MercadoLibreScrapperTest.java
package ar.edu.sip;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Tests unitarios/integración que validan la lógica del scraper usando Mockito en lugar de un
 * WebDriver real.
 *
 * <p>Cubren los 4 criterios del Hit 6: 1. Al menos 10 resultados por producto. 2. Schema mínimo del
 * JSON (titulo, link, tipos correctos). 3. Precios extraídos son números positivos. 4. Links son
 * URLs absolutas válidas.
 *
 * <p>REGLA MOCKITO: buildContainerList() SOLO construye la lista. Cada test hace su propio
 * when(driver.findElements(...)).thenReturn(...) para evitar el UnfinishedStubbingException que
 * ocurría cuando buildContainers() llamaba when(driver...) dentro de un contexto de stubbing
 * externo.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
// para permitir un helper compartido que genera stubs "de mas" por diseño.
class MercadoLibreScraperTest {

  @Mock WebDriver driver;
  @Mock WebDriverWait wait;

  @Mock WebElement container;
  @Mock WebElement linkElement;
  @Mock WebElement priceElement;

  // ── Helper: construye lista SIN stubbear driver ───────────────────────────
  private List<WebElement> buildContainerList(int cantidad) {
    List<WebElement> lista = new ArrayList<>();
    for (int i = 0; i < cantidad; i++) {
      WebElement c = mock(WebElement.class);
      WebElement link = mock(WebElement.class);
      WebElement price = mock(WebElement.class);
      WebElement store = mock(WebElement.class);
      WebElement ship = mock(WebElement.class);
      WebElement inst = mock(WebElement.class);

      when(link.getText()).thenReturn("Producto " + i);
      when(link.getAttribute("href"))
          .thenReturn("https://articulo.mercadolibre.com.ar/MLA-" + (1_000_000 + i));
      when(price.getText()).thenReturn("1500");
      when(store.getText()).thenReturn("por Tienda Oficial " + i);
      when(ship.getText()).thenReturn("Envío gratis");
      when(inst.getText()).thenReturn("12x sin interés");

      when(c.findElement(Selectors.PRODUCT_LINK)).thenReturn(link);
      when(c.findElement(Selectors.PRODUCT_PRICE)).thenReturn(price);
      when(c.findElement(Selectors.PRODUCT_OFFICIAL_STORE)).thenReturn(store);
      when(c.findElement(Selectors.PRODUCT_SHIPPING)).thenReturn(ship);
      when(c.findElement(Selectors.PRODUCT_INSTALLMENTS)).thenReturn(inst);

      lista.add(c);
    }
    return lista;
  }

  // ── 1. Cantidad mínima de resultados ──────────────────────────────────────
  @Test
  void extraerDatosConMasDe10ContenedoresRetornaExactamente10() {
    List<WebElement> lista = buildContainerList(15);
    when(driver.findElements(Selectors.CONTENEDOR_RESULTADOS)).thenReturn(lista);

    List<ProductResult> resultados = MercadoLibreScraper.extraerDatos(driver, "test");

    assertEquals(
        MercadoLibreScraper.CANT_RESULTADOS,
        resultados.size(),
        "Debe respetar el límite CANT_RESULTADOS aunque haya más contenedores");
  }

  @Test
  void extraerDatosCon10ContenedoresRetorna10() {
    List<WebElement> lista = buildContainerList(10);
    when(driver.findElements(Selectors.CONTENEDOR_RESULTADOS)).thenReturn(lista);

    List<ProductResult> resultados = MercadoLibreScraper.extraerDatos(driver, "test");

    assertEquals(10, resultados.size());
    assertEquals("Producto 0", resultados.get(0).getTitulo());
  }

  @Test
  void extraerDatosConMenosDe10ContenedoresRetornaTodosDisponibles() {
    List<WebElement> lista = buildContainerList(5);
    when(driver.findElements(Selectors.CONTENEDOR_RESULTADOS)).thenReturn(lista);

    List<ProductResult> resultados = MercadoLibreScraper.extraerDatos(driver, "test");

    assertEquals(5, resultados.size());
  }

  @Test
  void extraerDatosSinContenedoresRetornaListaVacia() {
    when(driver.findElements(Selectors.CONTENEDOR_RESULTADOS)).thenReturn(List.of());

    List<ProductResult> resultados = MercadoLibreScraper.extraerDatos(driver, "test");

    assertTrue(resultados.isEmpty());
  }

  // ── 2. Schema mínimo ──────────────────────────────────────────────────────

  @Test
  void extraerDatosTodosLosItemsTienenTituloYLink() {
    List<WebElement> lista = buildContainerList(10);

    when(driver.findElements(Selectors.CONTENEDOR_RESULTADOS)).thenReturn(lista);

    List<ProductResult> resultados = MercadoLibreScraper.extraerDatos(driver, "test");

    assertFalse(resultados.isEmpty());
    resultados.forEach(
        p -> {
          assertNotNull(p.getTitulo(), "titulo no debe ser null");
          assertFalse(p.getTitulo().isBlank(), "titulo no debe estar vacío");
          assertNotNull(p.getLink(), "link no debe ser null");
        });
  }

  @Test
  void extraerDatosItemConLinkFaltanteGuardaLinkNull() {
    // 9 contenedores válidos + 1 con link null
    List<WebElement> mixed = new ArrayList<>(buildContainerList(9));

    WebElement badContainer = mock(WebElement.class);
    WebElement badLink = mock(WebElement.class);

    when(badLink.getText()).thenReturn("Producto sin link");
    when(badLink.getAttribute("href")).thenReturn(null);
    when(badContainer.findElement(Selectors.PRODUCT_LINK)).thenReturn(badLink);
    when(badContainer.findElement(Selectors.PRODUCT_PRICE))
        .thenThrow(new NoSuchElementException("no price"));
    when(badContainer.findElement(Selectors.PRODUCT_OFFICIAL_STORE))
        .thenThrow(new NoSuchElementException("no store"));
    when(badContainer.findElement(Selectors.PRODUCT_SHIPPING))
        .thenThrow(new NoSuchElementException("no shipping"));
    when(badContainer.findElement(Selectors.PRODUCT_INSTALLMENTS))
        .thenThrow(new NoSuchElementException("no installments"));

    mixed.add(badContainer);

    when(driver.findElements(Selectors.CONTENEDOR_RESULTADOS)).thenReturn(mixed);

    List<ProductResult> resultados = MercadoLibreScraper.extraerDatos(driver, "test");

    assertEquals(10, resultados.size(), "Deben procesarse los 10 contenedores");

    long conLink = resultados.stream().filter(p -> p.getLink() != null).count();

    assertEquals(9, conLink, "Los 9 items válidos deben tener link");
  }

  // ── 3. Precios positivos ───────────────────────────────────────────────────

  @Test
  void extraerDatosPreciosSonTodosPositivos() {
    List<WebElement> lista = buildContainerList(10);

    when(driver.findElements(Selectors.CONTENEDOR_RESULTADOS)).thenReturn(lista);

    List<ProductResult> resultados = MercadoLibreScraper.extraerDatos(driver, "test");

    assertFalse(resultados.isEmpty(), "La lista no debe estar vacía");
    resultados.stream()
        .filter(p -> p.getPrecio() != null)
        .forEach(
            p ->
                assertTrue(
                    p.getPrecio() > 0, "Precio debe ser positivo, encontrado: " + p.getPrecio()));
  }

  @Test
  void extraerDatosPrecioConTextoNoNumericoSeteaNull() {
    WebElement c = mock(WebElement.class);
    WebElement link = mock(WebElement.class);
    WebElement price = mock(WebElement.class);

    when(link.getText()).thenReturn("Producto");
    when(link.getAttribute("href")).thenReturn("https://articulo.mercadolibre.com.ar/MLA-1");
    when(price.getText()).thenReturn("Precio a consultar");

    when(c.findElement(Selectors.PRODUCT_LINK)).thenReturn(link);
    when(c.findElement(Selectors.PRODUCT_PRICE)).thenReturn(price);
    when(c.findElement(Selectors.PRODUCT_OFFICIAL_STORE))
        .thenThrow(new NoSuchElementException("no store"));
    when(c.findElement(Selectors.PRODUCT_SHIPPING))
        .thenThrow(new NoSuchElementException("no shipping"));
    when(c.findElement(Selectors.PRODUCT_INSTALLMENTS))
        .thenThrow(new NoSuchElementException("no installments"));

    when(driver.findElements(Selectors.CONTENEDOR_RESULTADOS)).thenReturn(List.of(c));

    List<ProductResult> resultados = MercadoLibreScraper.extraerDatos(driver, "test");

    assertEquals(1, resultados.size());
    assertNull(resultados.get(0).getPrecio(), "Precio no numérico debe quedar como null");
  }

  // ── 4. Links absolutos ─────────────────────────────────────────────────────

  @Test
  void extraerDatosLinksSonUrlsAbsolutasValidas() {
    List<WebElement> lista = buildContainerList(10);
    when(driver.findElements(Selectors.CONTENEDOR_RESULTADOS)).thenReturn(lista);

    List<ProductResult> resultados = MercadoLibreScraper.extraerDatos(driver, "test");

    assertFalse(resultados.isEmpty());
    resultados.stream()
        .filter(p -> p.getLink() != null)
        .forEach(
            p -> {
              URI uri =
                  assertDoesNotThrow(
                      () -> new URI(p.getLink()), "link no es URI válida: " + p.getLink());
              assertTrue(uri.isAbsolute(), "link debe ser absoluta: " + p.getLink());
              assertTrue(
                  uri.getScheme().startsWith("http"), "scheme debe ser http/https: " + p.getLink());
            });
  }

  @Test
  void extraerDatosConErrorParcialEnUnItemContinuaProcesando() {
    // Crear un contenedor que explote al buscar el link
    WebElement badContainer = mock(WebElement.class);
    when(badContainer.findElement(Selectors.PRODUCT_LINK))
        .thenThrow(new NoSuchElementException("Simulado"));

    List<WebElement> lista = new ArrayList<>(buildContainerList(2));
    lista.add(0, badContainer); // Metemos el error al principio

    when(driver.findElements(Selectors.CONTENEDOR_RESULTADOS)).thenReturn(lista);

    List<ProductResult> resultados = MercadoLibreScraper.extraerDatos(driver, "test");

    // Debería tener 2 resultados (los buenos) y haber ignorado el roto
    assertEquals(2, resultados.size());
  }

  @Test
  void extraerDatosCubreVariantesDeEnvioYCuotas() {
    WebElement c = mock(WebElement.class);
    WebElement link = mock(WebElement.class);
    WebElement ship = mock(WebElement.class);
    WebElement inst = mock(WebElement.class);

    when(link.getText()).thenReturn("Producto Pro");
    when(link.getAttribute("href")).thenReturn("https://link.com");
    // Caso: SIN envío gratis y SIN cuotas (para entrar en los 'null' o false)
    when(ship.getText()).thenReturn("Envío a cargo del comprador");
    when(inst.getText()).thenReturn("Pagá con tarjeta");

    when(c.findElement(Selectors.PRODUCT_LINK)).thenReturn(link);
    when(c.findElement(Selectors.PRODUCT_SHIPPING)).thenReturn(ship);
    when(c.findElement(Selectors.PRODUCT_INSTALLMENTS)).thenReturn(inst);
    when(c.findElement(Selectors.PRODUCT_PRICE)).thenThrow(new NoSuchElementException(""));
    when(c.findElement(Selectors.PRODUCT_OFFICIAL_STORE)).thenThrow(new NoSuchElementException(""));

    when(driver.findElements(Selectors.CONTENEDOR_RESULTADOS)).thenReturn(List.of(c));

    List<ProductResult> resultados = MercadoLibreScraper.extraerDatos(driver, "test");

    assertFalse(resultados.get(0).isEnvioGratis());
    assertNull(resultados.get(0).getCuotasSinInteres());
  }

  @Test
  void esperarResultadosLanzaTimeoutExceptionSiNoAparecen() {
    // Simulamos que el wait falla
    when(wait.until(any())).thenThrow(new TimeoutException("Timeout simulado"));

    assertThrows(
        TimeoutException.class, () -> MercadoLibreScraper.esperarResultados(driver, wait, "test"));
  }

  @Test
  void cerrarBannerCookiesNoFallaSiElBotonNoExiste() {
    // Si findElement lanza excepción, el método debe capturarla silenciosamente
    when(driver.findElement(Selectors.BANNER_COOKIES))
        .thenThrow(new NoSuchElementException("No hay banner"));

    assertDoesNotThrow(() -> MercadoLibreScraper.cerrarBannerCookies(driver));
  }

  @Test
  void tomarScreenshotCreaArchivoCorrectamente() throws IOException {
    // Mockeamos la interfaz de Screenshot
    WebDriver screenshotDriver =
        mock(WebDriver.class, withSettings().extraInterfaces(TakesScreenshot.class));
    File tempFile = Files.createTempFile("test_ss", ".png").toFile();

    when(((TakesScreenshot) screenshotDriver).getScreenshotAs(OutputType.FILE))
        .thenReturn(tempFile);

    assertDoesNotThrow(
        () -> MercadoLibreScraper.tomarScreenshot(screenshotDriver, "test_prod", "chrome"));

    // Limpieza
    Files.deleteIfExists(tempFile.toPath());
    Files.deleteIfExists(Path.of("screenshots/test_prod_chrome.png"));
  }

  // ── tryGetText ─────────────────────────────────────────────────────────────

  @Test
  void tryGetTextElementoPresenteRetornaTextoLimpio() {
    when(container.findElement(Selectors.PRODUCT_LINK)).thenReturn(linkElement);
    when(linkElement.getText()).thenReturn("  Texto con espacios  ");

    String result = MercadoLibreScraper.tryGetText(container, Selectors.PRODUCT_LINK, "");

    assertEquals("Texto con espacios", result);
  }

  @Test
  void tryGetTextElementoAusenteRetornaNull() {
    when(container.findElement(Selectors.PRODUCT_LINK))
        .thenThrow(new NoSuchElementException("not found"));

    String result = MercadoLibreScraper.tryGetText(container, Selectors.PRODUCT_LINK, "");

    assertNull(result);
  }

  @Test
  void tryGetTextConPrefijoEliminaPrefijo() {
    when(container.findElement(Selectors.PRODUCT_OFFICIAL_STORE)).thenReturn(linkElement);
    when(linkElement.getText()).thenReturn("por Tienda Oficial");

    String result =
        MercadoLibreScraper.tryGetText(container, Selectors.PRODUCT_OFFICIAL_STORE, "por ");

    assertEquals("Tienda Oficial", result);
  }

  @Test
  void tryGetTextTextoVacioRetornaNull() {
    when(container.findElement(Selectors.PRODUCT_LINK)).thenReturn(linkElement);
    when(linkElement.getText()).thenReturn("   ");

    String result = MercadoLibreScraper.tryGetText(container, Selectors.PRODUCT_LINK, "");

    assertNull(result);
  }

  // ── tryGetLong ─────────────────────────────────────────────────────────────

  @Test
  void tryGetLongTextoNumericoConSeparadoresTetornaLong() {
    when(container.findElement(Selectors.PRODUCT_PRICE)).thenReturn(priceElement);
    when(priceElement.getText()).thenReturn("1.250.000");

    Long result = MercadoLibreScraper.tryGetLong(container, Selectors.PRODUCT_PRICE);

    assertEquals(1_250_000L, result);
  }

  @Test
  void tryGetLongTlementoAusenteTetornaNull() {
    when(container.findElement(Selectors.PRODUCT_PRICE))
        .thenThrow(new NoSuchElementException("not found"));

    Long result = MercadoLibreScraper.tryGetLong(container, Selectors.PRODUCT_PRICE);

    assertNull(result);
  }

  // ── sanitizar ──────────────────────────────────────────────────────────────

  @Test
  void sanitizarNombreConEspaciosYMayusculasGeneraSlugValido() {
    assertEquals("iphone_16_pro_max", MercadoLibreScraper.sanitizar("iPhone 16 Pro Max"));
  }

  @Test
  void sanitizarNombreConCaracteresEspecialesLosElimina() {
    String slug = MercadoLibreScraper.sanitizar("GeForce RTX 5090!!");
    assertFalse(slug.endsWith("_"), "No debe terminar con underscore");
    assertFalse(slug.contains("!"), "No debe contener '!'");
  }

  @Test
  void sanitizarEspaciosMultiplesColapsanEnUnGuion() {
    String slug = MercadoLibreScraper.sanitizar("bicicleta  rodado   29");
    assertFalse(slug.contains("__"), "No debe tener guiones dobles");
    assertEquals("bicicleta_rodado_29", slug);
  }

  // ── guardarJson ────────────────────────────────────────────────────────────

  @Test
  void guardarJsonListaValidaCreaArchivoLegible() throws IOException {
    ProductResult p = new ProductResult();
    p.setTitulo("Test");
    p.setPrecio(100_000L);
    p.setLink("https://example.com");

    MercadoLibreScraper.guardarJson("test_guardar_scraper", List.of(p));

    Path file = Path.of("output", "test_guardar_scraper.json");
    assertTrue(Files.exists(file), "El archivo JSON debe existir");
    assertTrue(Files.readString(file).contains("Test"), "El JSON debe contener el título");

    Files.deleteIfExists(file);
  }

  // ── filtros ────────────────────────────────────────────────────────────
  @Test
  void aplicarFiltroEjecutaJavaScriptCorrectamente() {
    // Creamos un mock que es WebDriver Y JavascriptExecutor a la vez
    WebDriver jsDriver =
        mock(WebDriver.class, withSettings().extraInterfaces(JavascriptExecutor.class));
    WebElement enlace = mock(WebElement.class);

    when(wait.until(any())).thenReturn(enlace);

    assertDoesNotThrow(() -> MercadoLibreScraper.aplicarFiltro(jsDriver, wait, "Nuevo", "test"));

    // Verificamos que se llamó al executeScript de JS
    verify((JavascriptExecutor) jsDriver, atLeastOnce()).executeScript(anyString(), eq(enlace));
  }

  // ── aplicarOrden ──────────────────────────────────────────────────────────

  @Test
  void aplicarOrdenExitosoEjecutaClickEnOpcion() {
    WebDriver jsDriver =
        mock(WebDriver.class, withSettings().extraInterfaces(JavascriptExecutor.class));
    WebElement boton = mock(WebElement.class);
    WebElement opcion = mock(WebElement.class);
    when(wait.until(any()))
        .thenReturn(boton)
        .thenReturn(mock(WebElement.class))
        .thenReturn(opcion)
        .thenReturn(mock(WebElement.class));

    assertDoesNotThrow(
        () -> MercadoLibreScraper.aplicarOrden(jsDriver, wait, "relevantes", "test"));

    verify((JavascriptExecutor) jsDriver, atLeastOnce()).executeScript(anyString(), eq(opcion));
  }

  @Test
  void aplicarOrdenFallaSilenciosamenteCuandoNoHayDropdown() {
    WebDriver jsDriver =
        mock(WebDriver.class, withSettings().extraInterfaces(JavascriptExecutor.class));
    when(wait.until(any())).thenThrow(new TimeoutException("no dropdown"));

    assertDoesNotThrow(
        () -> MercadoLibreScraper.aplicarOrden(jsDriver, wait, "relevantes", "test"));
  }

  // ── cerrarBannerCookies con botón visible ─────────────────────────────────

  @Test
  void cerrarBannerCookiesConBotonVisibleHaceClick() {
    WebElement boton = mock(WebElement.class);
    when(driver.findElement(Selectors.BANNER_COOKIES)).thenReturn(boton);
    when(boton.isDisplayed()).thenReturn(true);

    assertDoesNotThrow(() -> MercadoLibreScraper.cerrarBannerCookies(driver));

    verify(boton).click();
  }

  // ── ejecutarConReintentos ────────────────────────────────────────────────

  @Test
  void ejecutarConReintentosAgotaReintentosYNoLanzaExcepcion() {
    when(wait.until(any())).thenThrow(new TimeoutException("fallo simulado"));

    assertDoesNotThrow(
        () -> MercadoLibreScraper.ejecutarConReintentos(driver, wait, "test", "chrome"));
  }
}
