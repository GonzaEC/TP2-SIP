package ar.edu.sip;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

public class MercadoLibreScraper {

    private static final String URL_BASE = "https://www.mercadolibre.com.ar";
    private static final String PRODUCTO = "bicicleta rodado 29";
    private static final int TIMEOUT_SEGUNDOS = 20;
    private static final int CANTIDAD_PRODUCTOS = 5;

    // Selectores candidatos para los títulos, en orden de preferencia
    private static final String[] SELECTORES_TITULO = {
        "a.poly-component__title",               // layout "poly" (2024-2025)
        "h2.poly-box a",                          // variante con wrapper h2
        ".ui-search-item__title",                 // layout clásico
        "li.ui-search-layout__item h2 a",         // genérico por estructura
        "a.ui-search-link__title-card"            // fallback antiguo
    };

    public static void main(String[] args) {
        ChromeOptions options = new ChromeOptions();
        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        options.addArguments("--disable-blink-features=AutomationControlled");

        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(TIMEOUT_SEGUNDOS));

        try {
            driver.get(URL_BASE);

            WebElement campoBusqueda = wait.until(
                ExpectedConditions.elementToBeClickable(By.name("as_word"))
            );
            campoBusqueda.sendKeys(PRODUCTO);
            campoBusqueda.sendKeys(Keys.ENTER);

            // MercadoLibre AR redirige a listado.mercadolibre.com.ar/<slug>
            // Esperamos directamente al primer ítem de resultado en el DOM
            wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("li.ui-search-layout__item")
            ));

            // Probar cada selector hasta encontrar  uno que devuelva resultados
            List<WebElement> titulos = buscarTitulos(driver);

            if (titulos.isEmpty()) {
                System.out.println("[ERROR] No se encontraron títulos con ningún selector conocido.");
                System.out.println("  URL actual: " + driver.getCurrentUrl());
                System.out.println("  Título de página: " + driver.getTitle());
                return;
            }

            System.out.println("=== Primeros " + CANTIDAD_PRODUCTOS + " resultados para: \"" + PRODUCTO + "\" ===");
            System.out.println();

            int cantidad = Math.min(CANTIDAD_PRODUCTOS, titulos.size());
            for (int i = 0; i < cantidad; i++) {
                String texto = titulos.get(i).getText().trim();
                if (!texto.isEmpty()) {
                    System.out.printf("%d. %s%n", i + 1, texto);
                }
            }

        } finally {
            driver.quit();
        }
        System.exit(0);
    }

    private static List<WebElement> buscarTitulos(WebDriver driver) {
        for (String selector : SELECTORES_TITULO) {
            List<WebElement> elementos = driver.findElements(By.cssSelector(selector));
            // Filtrar elementos con texto no vacío
            List<WebElement> conTexto = elementos.stream()
                .filter(e -> !e.getText().trim().isEmpty())
                .toList();
            if (!conTexto.isEmpty()) {
                System.out.println("[INFO] Selector utilizado: " + selector);
                return conTexto;
            }
        }
        return List.of();
    }
}
