package ar.edu.sip;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

public class MercadoLibreScraper {

    private static final String URL_BASE       = "https://www.mercadolibre.com.ar";
    private static final String PRODUCTO       = "bicicleta rodado 29";
    private static final int    TIMEOUT_SEG    = 20;
    private static final int    CANT_PRODUCTOS = 5;

    private static final String[] SELECTORES_TITULO = {
        "a.poly-component__title",
        "h2.poly-box a",
        ".ui-search-item__title",
        "li.ui-search-layout__item h2 a",
        "a.ui-search-link__title-card"
    };

    public static void main(String[] args) throws IOException {
        String browserName = BrowserFactory.resolveName(null);
        WebDriver driver   = BrowserFactory.create(browserName);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(TIMEOUT_SEG));

        try {
            // ── 1.  Búsqueda ────────────────────────────────────────────────
            driver.get(URL_BASE);

            WebElement campo = wait.until(
                ExpectedConditions.elementToBeClickable(By.name("as_word"))
            );
            campo.sendKeys(PRODUCTO, Keys.ENTER);

            esperarResultados(wait);

            // ── 2. Cerrar banner de cookies si está presente ───────────────
            cerrarBannerCookies(driver, wait);

            // ── 3. Filtros ─────────────────────────────────────────────────
            aplicarFiltro(driver, wait, "Nuevo");
            aplicarFiltro(driver, wait, "Solo tiendas oficiales");
            aplicarOrden(driver, wait);

            // ── 3. Screenshot ──────────────────────────────────────────────
            String rutaScreenshot = tomarScreenshot(driver, PRODUCTO, browserName);
            System.out.println("[INFO] Screenshot guardado en: " + rutaScreenshot);

            // ── 4. Títulos filtrados ───────────────────────────────────────
            List<WebElement> titulos = resolverTitulos(driver);

            if (titulos.isEmpty()) {
                System.out.println("[ERROR] No se encontraron títulos.");
                System.out.println("  URL: " + driver.getCurrentUrl());
                return;
            }

            System.out.println();
            System.out.printf("=== Primeros %d resultados filtrados para: \"%s\" ===%n",
                              CANT_PRODUCTOS, PRODUCTO);
            System.out.println();

            int n = Math.min(CANT_PRODUCTOS, titulos.size());
            for (int i = 0; i < n; i++) {
                String texto = titulos.get(i).getText().trim();
                if (!texto.isEmpty()) System.out.printf("  %d. %s%n", i + 1, texto);
            }

        } finally {
            driver.quit();
        }
        System.exit(0);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Cierra el banner de consentimiento de cookies de MercadoLibre si aparece.
     * Usa un wait corto para no penalizar el tiempo total cuando el banner ya no existe.
     */
    private static void cerrarBannerCookies(WebDriver driver, WebDriverWait wait) {
        try {
            WebDriverWait waitCorto = new WebDriverWait(driver, Duration.ofSeconds(5));
            WebElement boton = waitCorto.until(
                ExpectedConditions.elementToBeClickable(
                    By.cssSelector(
                        "div.cookie-consent-banner-opt-out__container button, " +
                        "button[data-testid='action:understood-button'], " +
                        "button[data-testid='cookie-consent-accept-btn']"
                    )
                )
            );
            boton.click();
            // Esperar a que el banner desaparezca del DOM
            wait.until(ExpectedConditions.invisibilityOfElementLocated(
                By.cssSelector("div.cookie-consent-banner-opt-out__container")
            ));
            System.out.println("[INFO] Banner de cookies cerrado.");
        } catch (TimeoutException e) {
            // El banner no apareció o ya estaba cerrado
        }
    }

    private static void esperarResultados(WebDriverWait wait) {
        wait.until(ExpectedConditions.presenceOfElementLocated(
            By.cssSelector("li.ui-search-layout__item")
        ));
    }

    /**
     * Busca un enlace de filtro por su texto visible y hace click.
     * Después espera a que la página recargue los ítems de resultado.
     * Si el filtro no existe (ya aplicado o no disponible) imprime un aviso y continúa.
     */
    private static void aplicarFiltro(WebDriver driver, WebDriverWait wait, String texto) {
        // Selector ampliado: cubre tanto el panel lateral clásico como el layout poly actual
        String xpath =
            "//div[contains(@class,'ui-search-filter') or contains(@class,'facets')]" +
            "//a[normalize-space()='" + texto + "' or .//span[normalize-space()='" + texto + "']]" +
            " | " +
            "//aside//a[normalize-space()='" + texto + "' or .//span[normalize-space()='" + texto + "']]";
        try {
            WebElement enlace = wait.until(
                ExpectedConditions.presenceOfElementLocated(By.xpath(xpath))
            );
            ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block:'center'});", enlace
            );
            // JS click evita ElementClickInterceptedException por overlays residuales
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", enlace);
            esperarResultados(wait);
            System.out.println("[INFO] Filtro aplicado: " + texto);
        } catch (TimeoutException e) {
            System.out.println("[WARN] Filtro no encontrado o ya activo: " + texto);
        }
    }

    /**
     * Aplica el ordenamiento "Más relevantes" sobre el dropdown de Andes.
     * El listbox abre sobre el trigger; hay que clickear el <li role="option">,
     * no el <span> interior que tiene pointer-events bloqueados por el overlay.
     */
    private static void aplicarOrden(WebDriver driver, WebDriverWait wait) {
        try {
            // Abrir el dropdown de orden
            WebElement botonOrden = wait.until(
                ExpectedConditions.elementToBeClickable(
                    By.cssSelector("button.andes-dropdown__trigger, div.ui-search-sort-filter button")
                )
            );
            botonOrden.click();

            // Esperar a que el listbox esté visible
            wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("ul[role='listbox']")
            ));

            // Clickear el <li role="option"> que contiene "relevantes" usando JS
            WebElement opcion = wait.until(
                ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//li[@role='option'][.//span[contains(normalize-space(),'relevantes')] " +
                             "or contains(normalize-space(),'relevantes')]")
                )
            );
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", opcion);
            esperarResultados(wait);
            System.out.println("[INFO] Orden aplicado: Más relevantes");

        } catch (TimeoutException e) {
            System.out.println("[WARN] Dropdown de orden no encontrado (es el orden por defecto).");
        }
    }

    /** Guarda un screenshot en screenshots/<producto>_<browser>.png */
    private static String tomarScreenshot(WebDriver driver, String producto, String browser)
            throws IOException {
        String nombreArchivo = sanitizar(producto) + "_" + browser + ".png";
        Path directorio = Paths.get("screenshots");
        Files.createDirectories(directorio);
        Path destino = directorio.resolve(nombreArchivo);

        File tmp = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        Files.copy(tmp.toPath(), destino,
                   java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return destino.toAbsolutePath().toString();
    }

    /** Reemplaza espacios y caracteres no alfanuméricos por guión bajo. */
    private static String sanitizar(String texto) {
        return texto.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("_+$", "");
    }

    private static List<WebElement> resolverTitulos(WebDriver driver) {
        for (String selector : SELECTORES_TITULO) {
            List<WebElement> lista = driver.findElements(By.cssSelector(selector));
            List<WebElement> conTexto = lista.stream()
                .filter(e -> !e.getText().trim().isEmpty())
                .toList();
            if (!conTexto.isEmpty()) {
                System.out.println("[INFO] Selector de títulos utilizado: " + selector);
                return conTexto;
            }
        }
        return List.of();
    }
}
