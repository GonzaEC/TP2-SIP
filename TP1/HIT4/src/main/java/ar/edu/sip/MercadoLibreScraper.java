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
    private static final int TIMEOUT_SEG = 20;
    private static final int CANT_PRODUCTOS = 10;

    private static final String[] PRODUCTOS_BUSQUEDA = {
        "bicicleta rodado 29",
        "iPhone 16 Pro Max",
        "GeForce RTX 5090"
    };

    public static void main(String[] args) throws IOException {
        String browserName = BrowserFactory.resolveName(null);
        WebDriver driver = BrowserFactory.create(browserName);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(TIMEOUT_SEG));

        try {
            for (String producto : PRODUCTOS_BUSQUEDA) {
                System.out.println("[INFO] Procesando producto: " + producto);
                procesarProducto(driver, wait, producto, browserName);
            }
        } finally {
            driver.quit();
        }
        System.exit(0);
    }

    private static void procesarProducto(WebDriver driver, WebDriverWait wait, String producto, String browser) throws IOException {
        driver.get(URL_BASE);

        WebElement campo = wait.until(ExpectedConditions.elementToBeClickable(By.name("as_word")));
        campo.clear();
        campo.sendKeys(producto, Keys.ENTER);

        esperarResultados(wait);
        cerrarBannerCookies(driver, wait);

        // Aplicar filtros (como en Hit #3)
        aplicarFiltro(driver, wait, "Nuevo");
        aplicarFiltro(driver, wait, "Solo tiendas oficiales");
        aplicarOrden(driver, wait);

        // Extraer datos
        List<ProductResult> resultados = extraerDatos(driver);

        // Guardar JSON
        guardarJson(producto, resultados);

        // Screenshot (opcional , pero buena práctica mantener lo de Hit #3 si es posible)
        tomarScreenshot(driver, producto, browser);
    }

    private static List<ProductResult> extraerDatos(WebDriver driver) {
        List<ProductResult> lista = new ArrayList<>();
        // Selectores de los contenedores de cada producto
        List<WebElement> contenedores = driver.findElements(By.cssSelector("li.ui-search-layout__item, .ui-search-result"));

        int limite = Math.min(CANT_PRODUCTOS, contenedores.size());
        for (int i = 0; i < limite; i++) {
            WebElement c = contenedores.get(i);
            ProductResult pr = new ProductResult();

            try {
                // Título y Link
                WebElement linkElement = c.findElement(By.cssSelector("a.poly-component__title, .ui-search-item__title, .ui-search-link__title-card, .ui-search-item__group__element.ui-search-link"));
                pr.setTitulo(linkElement.getText().trim());
                pr.setLink(linkElement.getAttribute("href"));

                // Precio
                try {
                    String precioTexto = c.findElement(By.cssSelector(".andes-money-amount__fraction")).getText();
                    pr.setPrecio(Long.parseLong(precioTexto.replaceAll("[^0-9]", "")));
                } catch (NoSuchElementException e) {
                    pr.setPrecio(null);
                }

                // Tienda Oficial
                try {
                    WebElement tiendaElement = c.findElement(By.cssSelector(".ui-search-item__group__element--official-store-badge, .poly-component__seller"));
                    String tienda = tiendaElement.getText().replace("por ", "").trim();
                    pr.setTiendaOficial(tienda.isEmpty() ? null : tienda);
                } catch (NoSuchElementException e) {
                    pr.setTiendaOficial(null);
                }

                // Envío Gratis
                try {
                    WebElement envioElement = c.findElement(By.cssSelector(".ui-search-item__shipping--free, .poly-component__shipping"));
                    pr.setEnvioGratis(envioElement.getText().toLowerCase().contains("gratis"));
                } catch (NoSuchElementException e) {
                    pr.setEnvioGratis(false);
                }

                // Cuotas sin interés
                try {
                    WebElement cuotasElement = c.findElement(By.cssSelector(".ui-search-item__group__element--installments, .poly-component__installments"));
                    String cuotas = cuotasElement.getText().trim();
                    pr.setCuotasSinInteres(cuotas.toLowerCase().contains("sin interés") ? cuotas : null);
                } catch (NoSuchElementException e) {
                    pr.setCuotasSinInteres(null);
                }

                lista.add(pr);
            } catch (Exception e) {
                System.out.println("[WARN] Error al extraer un resultado: " + e.getMessage());
            }
        }
        return lista;
    }

    private static void guardarJson(String producto, List<ProductResult> resultados) throws IOException {
        Path dir = Paths.get("output");
        Files.createDirectories(dir);
        String nombreArchivo = sanitizar(producto) + ".json";
        Path destino = dir.resolve(nombreArchivo);

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(destino.toFile(), resultados);
        System.out.println("[INFO] JSON guardado en: " + destino.toAbsolutePath());
    }

    // Métodos heredados de Hit #3 (con ajustes si es necesario)

    private static void cerrarBannerCookies(WebDriver driver, WebDriverWait wait) {
        try {
            WebDriverWait waitCorto = new WebDriverWait(driver, Duration.ofSeconds(3));
            WebElement boton = waitCorto.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("button[data-testid='action:understood-button'], button[data-testid='cookie-consent-accept-btn']")
            ));
            boton.click();
        } catch (Exception e) { /* Ignorar si no aparece */ }
    }

    private static void esperarResultados(WebDriverWait wait) {
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("li.ui-search-layout__item, .ui-search-result")));
    }

    private static void aplicarFiltro(WebDriver driver, WebDriverWait wait, String texto) {
        String xpath = "//aside//a[normalize-space()='" + texto + "' or .//span[normalize-space()='" + texto + "']]";
        try {
            WebElement enlace = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)));
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", enlace);
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", enlace);
            esperarResultados(wait);
            System.out.println("[INFO] Filtro aplicado: " + texto);
        } catch (Exception e) {
            System.out.println("[WARN] Filtro no encontrado: " + texto);
        }
    }

    private static void aplicarOrden(WebDriver driver, WebDriverWait wait) {
        try {
            WebElement botonOrden = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("button.andes-dropdown__trigger")));
            botonOrden.click();
            WebElement opcion = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//li[@role='option'][.//span[contains(normalize-space(),'relevantes')]]")
            ));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", opcion);
            esperarResultados(wait);
            System.out.println("[INFO] Orden aplicado: Más relevantes");
        } catch (Exception e) {
            System.out.println("[WARN] No se pudo aplicar el orden.");
        }
    }

    private static void tomarScreenshot(WebDriver driver, String producto, String browser) throws IOException {
        Path dir = Paths.get("screenshots");
        Files.createDirectories(dir);
        String nombre = sanitizar(producto) + "_" + browser + ".png";
        File tmp = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        Files.copy(tmp.toPath(), dir.resolve(nombre), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static String sanitizar(String texto) {
        return texto.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("_+$", "");
    }
}
