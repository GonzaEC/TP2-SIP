package ar.edu.sip;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Scraper de MercadoLibre con paginación (hasta 3 páginas / 30 resultados) y persistencia en
 * PostgreSQL. TP2 -PARTE 3 -HIT 5: emite logs via OTLP/gRPC al collector DaemonSet (además de JSON
 * a stdout). No se modifican los call-sites LOG.info/warn/error — el bridge Logback -> OTel lo hace
 * automaticamente via OpenTelemetryAppender en logback.xml + OtelSetup.init(). HIT 6 (Bonus): cada
 * producto se envuelve en un span de tracing. Los logs emitidos dentro del span heredan su trace_id
 * y span_id, permitiendo correlación log <-> trace en Loki/Kibana/Jaeger.
 */
public class MercadoLibreScraper {

  private static final Logger LOG = LoggerFactory.getLogger(MercadoLibreScraper.class);

  private static final String URL_BASE = "https://www.mercadolibre.com.ar";
  private static final int TIMEOUT_SEG = 30;
  private static final int MAX_REINTENTOS = 3;

  static final int CANT_RESULTADOS = 30;
  static final int CANT_POR_PAGINA = 10;
  static final int MAX_PAGINAS = 3;

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
    // TP2 - PARTE 3 - HIT 5: inicializar OTel SDK antes de cualquier log.
    // Registra el shutdown hook que vacía el buffer BatchLogRecordProcessor
    // antes de que el JVM termine (crítico en Jobs de corta duración).
    OpenTelemetry otel = OtelSetup.init();

    // TP2 - PARTE 3 - HIT 6: Tracer para crear spans por producto.
    Tracer tracer = otel.getTracer("ar.edu.sip.scraper", "1.0");

    String browser = BrowserFactory.resolveName(null);
    boolean headless = BrowserFactory.resolveHeadless();
    String[] productos = resolveProductos();
    WebDriver driver = BrowserFactory.create(browser, headless);
    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(TIMEOUT_SEG));

    MDC.put("browser", browser);
    LOG.info("MercadoLibre Scraper iniciado");

    try {
      for (String producto : productos) {
        MDC.put("producto", producto);
        LOG.info("Iniciando scrape");

        // HIT 6: span que envuelve todo el proceso de un producto.
        // Los logs emitidos dentro del scope heredan el trace_id de este span.
        // En Loki: {service="scraper"} | json | trace_id != ""
        Span span =
            tracer
                .spanBuilder("scrape.producto")
                .setAttribute("producto", producto)
                .setAttribute("browser", browser)
                .startSpan();

        try (Scope ignored = span.makeCurrent()) {
          ejecutarConReintentos(driver, wait, producto, browser);
        } catch (Exception e) {
          span.setStatus(StatusCode.ERROR, e.getMessage());
          span.recordException(e);
          throw e;
        } finally {
          span.end();
        }

        MDC.remove("producto");
      }
    } finally {
      driver.quit();
      MDC.clear();
    }
    System.exit(0);
  }

  // ── Métodos sin cambios respecto al HIT 8 original ───────────────────────

  static void ejecutarConReintentos(
      WebDriver driver, WebDriverWait wait, String producto, String browser) {
    int intento = 1;
    while (intento <= MAX_REINTENTOS) {
      MDC.put("intento", String.valueOf(intento));
      try {
        procesarProducto(driver, wait, producto, browser);
        MDC.remove("intento");
        return;
      } catch (Exception e) {
        LOG.error("Fallo en intento", kv("error_msg", e.getMessage()), e);
        if (intento == MAX_REINTENTOS) {
          LOG.error("Se agotaron los reintentos", kv("max_reintentos", MAX_REINTENTOS));
          break;
        }
        LOG.info("Reintentando", kv("proximo_intento", intento + 1));
        intento++;
      }
    }
    MDC.remove("intento");
  }

  static void procesarProducto(
      WebDriver driver, WebDriverWait wait, String producto, String browser) throws IOException {
    Instant inicio = Instant.now();
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
    List<ProductResult> resultados = extraerDatosConPaginacion(driver, wait, producto);
    PriceStats stats = PriceStats.calcular(resultados);
    stats.imprimirResumen(producto);
    long duracionMs = Duration.between(inicio, Instant.now()).toMillis();
    LOG.info(
        "Scrape completado", kv("items_found", resultados.size()), kv("duration_ms", duracionMs));
    guardarJson(producto, resultados);
    PostgresWriter.guardar(producto, resultados, stats);
    tomarScreenshot(driver, producto, browser);
  }

  static List<ProductResult> extraerDatosConPaginacion(
      WebDriver driver, WebDriverWait wait, String producto) {
    List<ProductResult> todos = new ArrayList<>();
    int pagina = 1;
    while (pagina <= MAX_PAGINAS && todos.size() < CANT_RESULTADOS) {
      List<ProductResult> paginaActual = extraerDatos(driver, producto);
      todos.addAll(paginaActual);
      LOG.info(
          "Página procesada",
          kv("page", pagina),
          kv("items_pagina", paginaActual.size()),
          kv("items_acumulados", todos.size()),
          kv("items_objetivo", CANT_RESULTADOS));
      if (todos.size() >= CANT_RESULTADOS || !irSiguientePagina(driver, wait)) {
        break;
      }
      pagina++;
    }
    return todos.size() > CANT_RESULTADOS
        ? new ArrayList<>(todos.subList(0, CANT_RESULTADOS))
        : todos;
  }

  static boolean irSiguientePagina(WebDriver driver, WebDriverWait wait) {
    try {
      WebElement sig = driver.findElement(Selectors.BOTON_SIGUIENTE_PAGINA);
      ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", sig);
      ((JavascriptExecutor) driver).executeScript("arguments[0].click();", sig);
      wait.until(ExpectedConditions.stalenessOf(sig));
      wait.until(ExpectedConditions.presenceOfElementLocated(Selectors.CONTENEDOR_RESULTADOS));
      return true;
    } catch (Exception e) {
      LOG.info("No hay siguiente página.");
      return false;
    }
  }

  public static List<ProductResult> extraerDatos(WebDriver driver, String producto) {
    List<ProductResult> lista = new ArrayList<>();
    List<WebElement> contenedores = driver.findElements(Selectors.CONTENEDOR_RESULTADOS);
    int limite = Math.min(CANT_POR_PAGINA, contenedores.size());
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
        LOG.warn("Error parcial en item", kv("item_index", i + 1), kv("error_msg", e.getMessage()));
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
      LOG.warn("Filtro no aplicado", kv("filtro", texto), kv("error_msg", e.getMessage()));
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
      LOG.warn("Orden no aplicado", kv("orden", texto), kv("error_msg", e.getMessage()));
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
    LOG.info("JSON guardado", kv("path", destino.toAbsolutePath().toString()));
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
