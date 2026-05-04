package ar.edu.sip;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class MercadoLibreScraper {

    private static final String URL_BASE = "https://www.mercadolibre.com.ar";
    private static final int TIMEOUT_SEG = 15;
    private static final int CANT_RESULTADOS = 10;
    private static final int MAX_REINTENTOS = 3;

    private static final String[] PRODUCTOS = {
        "bicicleta rodado 29",
        "iPhone 16 Pro Max",
        "GeForce RTX 5090"
    };

    public static void main(String[] args) throws IOException {
        String browser = BrowserFactory.resolveName(null);
        WebDriver driver = BrowserFactory.create(browser);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(TIMEOUT_SEG));

        try {
            for (String producto : PRODUCTOS) {
                System.out.println("\n[PROCESS] Iniciando: " + producto);
                ejecutarConReintentos(driver, wait, producto, browser);
            }
        } finally {
            driver.quit();
        }
        System.exit(0);
    }

    private static void ejecutarConReintentos(WebDriver driver, WebDriverWait wait, String producto, String browser) {
        int intento = 1;

        while (intento <= MAX_REINTENTOS) {
            try {
                procesarProducto(driver, wait, producto, browser);
                return;
            } catch (Exception e) {
                System.err.printf("[ERROR] Fallo en intento %d/%d para '%s' (%s): %s%n",
                                intento, MAX_REINTENTOS, producto, browser, e.getMessage());

                if (intento == MAX_REINTENTOS) {
                    System.err.println("[CRITICAL] Se agotaron los reintentos para: " + producto);
                    break;
                }

                System.out.println("[RETRY] Reintentando...");
                intento++;
            }
        }
    }

    private static void procesarProducto(WebDriver driver, WebDriverWait wait, String producto, String browser) throws IOException {
        driver.get(URL_BASE);

        // Búsqueda
        WebElement campo = wait.until(ExpectedConditions.elementToBeClickable(Selectors.INPUT_BUSQUEDA));
        campo.clear();
        campo.sendKeys(producto, Keys.ENTER);

        esperarResultados(wait, producto);
        cerrarBannerCookies(driver);

        // Filtros
        aplicarFiltro(driver, wait, "Nuevo", producto);
        aplicarFiltro(driver, wait, "Solo tiendas oficiales", producto);
        aplicarOrden(driver, wait, "relevantes", producto);

        // Extracción
        List<ProductResult> resultados = extraerDatos(driver, producto);

        // Guardar
        guardarJson(producto, resultados);
        tomarScreenshot(driver, producto, browser);
    }

    private static List<ProductResult> extraerDatos(WebDriver driver, String producto) {
        List<ProductResult> lista = new ArrayList<>();
        List<WebElement> contenedores = driver.findElements(Selectors.CONTENEDOR_RESULTADOS);

        int limite = Math.min(CANT_RESULTADOS, contenedores.size());
        for (int i = 0; i < limite; i++) {
            WebElement c = contenedores.get(i);
            ProductResult pr = new ProductResult();

            try {
                // Título y Link (Obligatorios para considerar el resultado válido)
                WebElement linkElem = c.findElement(Selectors.PRODUCT_LINK);
                pr.setTitulo(linkElem.getText().trim());
                pr.setLink(linkElem.getAttribute("href"));

                // Precio (Opcional)
                pr.setPrecio(tryGetLong(c, Selectors.PRODUCT_PRICE));

                // Tienda Oficial (Opcional)
                pr.setTiendaOficial(tryGetText(c, Selectors.PRODUCT_OFFICIAL_STORE, "por "));

                // Envío Gratis (Opcional)
                String envio = tryGetText(c, Selectors.PRODUCT_SHIPPING, "");
                pr.setEnvioGratis(envio != null && envio.toLowerCase().contains("gratis"));

                // Cuotas (Opcional)
                String cuotas = tryGetText(c, Selectors.PRODUCT_INSTALLMENTS, "");
                pr.setCuotasSinInteres(cuotas != null && cuotas.toLowerCase().contains("sin interés") ? cuotas : null);

                lista.add(pr);
            } catch (Exception e) {
                System.err.printf("[WARN] Error parcial en item %d de '%s': %s%n", i + 1, producto, e.getMessage());
            }
        }
        return lista;
    }

    // ── Robustez: Helpers para campos opcionales ─────────────────────────────

    private static String tryGetText(WebElement parent, By selector, String removePrefix) {
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

    private static Long tryGetLong(WebElement parent, By selector) {
        String val = tryGetText(parent, selector, "");
        if (val == null) return null;
        try {
            return Long.parseLong(val.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ── Robustez: Métodos con logs de contexto ────────────────────────────────

    private static void esperarResultados(WebDriverWait wait, String producto) {
        try {
            wait.until(ExpectedConditions.presenceOfElementLocated(Selectors.CONTENEDOR_RESULTADOS));
        } catch (TimeoutException e) {
            throw new TimeoutException("No cargaron resultados para '" + producto + "' usando " + Selectors.CONTENEDOR_RESULTADOS);
        }
    }

    private static void aplicarFiltro(WebDriver driver, WebDriverWait wait, String texto, String producto) {
        By selector = Selectors.filtroPorTexto(texto);
        try {
            WebElement enlace = wait.until(ExpectedConditions.presenceOfElementLocated(selector));
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", enlace);
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", enlace);
            esperarResultados(wait, producto);
        } catch (Exception e) {
            System.err.printf("[WARN] Filtro '%s' no aplicado en '%s': %s%n", texto, producto, e.getMessage());
        }
    }

    private static void aplicarOrden(WebDriver driver, WebDriverWait wait, String texto, String producto) {
        try {
            WebElement boton = wait.until(ExpectedConditions.elementToBeClickable(Selectors.DROPDOWN_ORDEN));
            boton.click();
            wait.until(ExpectedConditions.visibilityOfElementLocated(Selectors.LISTBOX_ORDEN));
            WebElement opcion = wait.until(ExpectedConditions.presenceOfElementLocated(Selectors.opcionOrden(texto)));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", opcion);
            esperarResultados(wait, producto);
        } catch (Exception e) {
            System.err.printf("[WARN] Orden '%s' no aplicado en '%s': %s%n", texto, producto, e.getMessage());
        }
    }

    private static void cerrarBannerCookies(WebDriver driver) {
        try {
            WebElement boton = driver.findElement(Selectors.BANNER_COOKIES);
            if (boton.isDisplayed()) boton.click();
        } catch (Exception e) { /* Ignorar */ }
    }

    private static void guardarJson(String producto, List<ProductResult> resultados) throws IOException {
        Path dir = Paths.get("output");
        Files.createDirectories(dir);
        Path destino = dir.resolve(sanitizar(producto) + ".json");
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(destino.toFile(), resultados);
        System.out.println("[SUCCESS] JSON: " + destino.toAbsolutePath());
    }

    private static void tomarScreenshot(WebDriver driver, String producto, String browser) throws IOException {
        Path dir = Paths.get("screenshots");
        Files.createDirectories(dir);
        File tmp = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        Files.copy(tmp.toPath(), dir.resolve(sanitizar(producto) + "_" + browser + ".png"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static String sanitizar(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("_+$", "");
    }
}
