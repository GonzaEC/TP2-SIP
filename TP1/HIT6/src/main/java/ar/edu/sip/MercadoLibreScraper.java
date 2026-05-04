package ar.edu.sip;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MercadoLibreScraper {

  private static final Logger LOG = LoggerFactory.getLogger(MercadoLibreScraper.class);

  private static final String URL_BASE = "https://www.mercadolibre.com.ar";
  // 30s da margen para entornos con red lenta (CI runners) sin colgarse demasiado.
  private static final int TIMEOUT_SEG = 30;
  private static final int MAX_REINTENTOS = 3;

  static final int CANT_RESULTADOS = 10;

  static final String[] PRODUCTOS_DEFAULT = {
    "bicicleta rodado 29", "iPhone 16 Pro Max", "GeForce RTX 5090"
  };

  static String[] resolveProductos() {
    String env = System.getenv("PRODUCTS");
    if (env != null && !env.isBlank()) {
      return env.lines().map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
    }
    return PRODUCTOS_DEFAULT;
  }

  public static void main(String[] args) throws IOException {
    String browser = BrowserFactory.resolveName(null);
    boolean headless = BrowserFactory.resolveHeadless();
    String[] productos = resolveProductos();
    WebDriver driver = BrowserFactory.create(browser, headless);
    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(TIMEOUT_SEG));

    try {
      for (String producto : productos) {
        LOG.info("Iniciando: {}", producto);
        ejecutarConReintentos(driver, wait, producto, browser);
      }
    } finally {
      driver.quit();
    }
    System.exit(0);
  }

  static void ejecutarConReintentos(
      WebDriver driver, WebDriverWait wait, String producto, String browser) {
    int intento = 1;

    while (intento <= MAX_REINTENTOS) {
      try {
        procesarProducto(driver, wait, producto, browser);
        return;
      } catch (Exception e) {
        LOG.error(
            "Fallo en intento {}/{} para '{}' ({}): {}",
            intento,
            MAX_REINTENTOS,
            producto,
            browser,
            e.getMessage());
        if (intento == MAX_REINTENTOS) {
          LOG.error("Se agotaron los reintentos para: {}", producto);
          break;
        }
        LOG.info("Reintentando...");
        intento++;
      }
    }
  }

  static void procesarProducto(
      WebDriver driver, WebDriverWait wait, String producto, String browser) throws IOException {
    driver.get(URL_BASE);

    WebElement campo =
        wait.until(ExpectedConditions.elementToBeClickable(Selectors.INPUT_BUSQUEDA));

    campo.clear();
    campo.sendKeys(producto, Keys.ENTER);

    esperarResultados(driver, wait, producto);
    cerrarBannerCookies(driver);

    aplicarFiltro(driver, wait, "Nuevo", producto);
    aplicarFiltro(driver, wait, "Solo tiendas oficiales", producto);
    aplicarOrden(driver, wait, "relevantes", producto);

    List<ProductResult> resultados = extraerDatos(driver, producto);

    guardarJson(producto, resultados);
    tomarScreenshot(driver, producto, browser);
  }

  public static List<ProductResult> extraerDatos(WebDriver driver, String producto) {
    List<ProductResult> lista = new ArrayList<>();

    List<WebElement> contenedores = driver.findElements(Selectors.CONTENEDOR_RESULTADOS);

    int limite = Math.min(CANT_RESULTADOS, contenedores.size());

    for (int i = 0; i < limite; i++) {
      WebElement c = contenedores.get(i);
      ProductResult pr = new ProductResult();

      try {
        WebElement linkElem = c.findElement(Selectors.PRODUCT_LINK);

        pr.setTitulo(linkElem.getText().trim());
        pr.setLink(linkElem.getAttribute("href"));

        pr.setPrecio(tryGetLong(c, Selectors.PRODUCT_PRICE));
        pr.setTiendaOficial(tryGetText(c, Selectors.PRODUCT_OFFICIAL_STORE, "por "));

        String envio = tryGetText(c, Selectors.PRODUCT_SHIPPING, "");

        pr.setEnvioGratis(envio != null && envio.toLowerCase().contains("gratis"));

        String cuotas = tryGetText(c, Selectors.PRODUCT_INSTALLMENTS, "");

        pr.setCuotasSinInteres(
            cuotas != null && cuotas.toLowerCase().contains("sin interés") ? cuotas : null);

        lista.add(pr);
      } catch (Exception e) {
        LOG.warn("Error parcial en item {} de '{}': {}", i + 1, producto, e.getMessage());
      }
    }
    return lista;
  }

  static String tryGetText(WebElement parent, By selector, String removePrefix) {
    try {
      String text = parent.findElement(selector).getText().trim();

      if (removePrefix != null && !removePrefix.isEmpty()) {
        text = text.replace(removePrefix, "").trim();
      }

      return text.isEmpty() ? null : text;
    } catch (NoSuchElementException e) {
      return null;
    }
  }

  static Long tryGetLong(WebElement parent, By selector) {
    String val = tryGetText(parent, selector, "");
    if (val == null) {
      return null;
    }
    try {
      return Long.parseLong(val.replaceAll("[^0-9]", ""));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  static void esperarResultados(WebDriver driver, WebDriverWait wait, String producto) {
    try {
      wait.until(ExpectedConditions.presenceOfElementLocated(Selectors.CONTENEDOR_RESULTADOS));
    } catch (TimeoutException e) {
      // Loguear URL y title reales para diagnosticar bloqueos / captchas / cambios de DOM.
      String url = "(no disponible)";
      String title = "(no disponible)";
      try {
        url = driver.getCurrentUrl();
        title = driver.getTitle();
      } catch (Exception ignored) {
        // El driver puede estar en mal estado; no romper el log
      }
      throw new TimeoutException(
          "No cargaron resultados para '" + producto + "' (url=" + url + ", title=" + title + ")");
    }
  }

  static void aplicarFiltro(WebDriver driver, WebDriverWait wait, String texto, String producto) {
    By selector = Selectors.filtroPorTexto(texto);
    try {
      WebElement enlace = wait.until(ExpectedConditions.presenceOfElementLocated(selector));

      ((JavascriptExecutor) driver)
          .executeScript("arguments[0].scrollIntoView({block:'center'});", enlace);

      ((JavascriptExecutor) driver).executeScript("arguments[0].click();", enlace);
      esperarResultados(driver, wait, producto);
    } catch (Exception e) {
      LOG.warn("Filtro '{}' no aplicado en '{}': {}", texto, producto, e.getMessage());
    }
  }

  static void aplicarOrden(WebDriver driver, WebDriverWait wait, String texto, String producto) {
    try {
      WebElement boton =
          wait.until(ExpectedConditions.elementToBeClickable(Selectors.DROPDOWN_ORDEN));

      ((JavascriptExecutor) driver).executeScript("arguments[0].click();", boton);

      wait.until(ExpectedConditions.visibilityOfElementLocated(Selectors.LISTBOX_ORDEN));

      WebElement opcion =
          wait.until(ExpectedConditions.presenceOfElementLocated(Selectors.opcionOrden(texto)));

      ((JavascriptExecutor) driver).executeScript("arguments[0].click();", opcion);
      esperarResultados(driver, wait, producto);
    } catch (Exception e) {
      LOG.warn("Orden '{}' no aplicado en '{}': {}", texto, producto, e.getMessage());
    }
  }

  static void cerrarBannerCookies(WebDriver driver) {
    try {
      WebElement boton = driver.findElement(Selectors.BANNER_COOKIES);
      if (boton.isDisplayed()) {
        boton.click();
      }
    } catch (Exception e) {
      /* Ignorar */
    }
  }

  public static void guardarJson(String producto, List<ProductResult> resultados)
      throws IOException {
    Path dir = Paths.get("output");

    Files.createDirectories(dir);

    Path destino = dir.resolve(sanitizar(producto) + ".json");

    new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .writeValue(destino.toFile(), resultados);

    LOG.info("JSON guardado: {}", destino.toAbsolutePath());
  }

  static void tomarScreenshot(WebDriver driver, String producto, String browser)
      throws IOException {
    Path dir = Paths.get("screenshots");

    Files.createDirectories(dir);

    File tmp = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);

    Files.copy(
        tmp.toPath(),
        dir.resolve(sanitizar(producto) + "_" + browser + ".png"),
        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
  }

  public static String sanitizar(String s) {
    return s.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("_+$", "");
  }
}
