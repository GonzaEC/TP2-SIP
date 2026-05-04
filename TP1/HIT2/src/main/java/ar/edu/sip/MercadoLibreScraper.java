package ar.edu.sip;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

public class MercadoLibreScraper {

    private static final String URL_BASE        = "https://www.mercadolibre.com.ar";
    private static final String PRODUCTO        = "bicicleta rodado 29";
    private static final int    TIMEOUT_SEG     = 20;
    private static final int    CANT_PRODUCTOS  = 5;

    private static final String[] SELECTORES_TITULO = {
        "a.poly-component__title",
        "h2.poly-box a",
        ".ui-search-item__title",
        "li.ui-search-layout__item h2 a",
        "a.ui-search-link__title-card"
    };

    public static void main(String[] args) {
        //  BrowserFactory resuelve: argumento > system property > env var > "chrome"
        WebDriver driver = BrowserFactory.create();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(TIMEOUT_SEG));

        try {
            driver.get(URL_BASE);

            WebElement campoBusqueda = wait.until(
                ExpectedConditions.elementToBeClickable(By.name("as_word"))
            );
            campoBusqueda.sendKeys(PRODUCTO);
            campoBusqueda.sendKeys(Keys.ENTER);

            // MercadoLibre AR redirige a listado.mercadolibre.com.ar/<slug>
            wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("li.ui-search-layout__item")
            ));

            List<WebElement> titulos = resolverTitulos(driver);

            if (titulos.isEmpty()) {
                System.out.println("[ERROR] No se encontraron títulos con ningún selector conocido.");
                System.out.println("  URL actual:       " + driver.getCurrentUrl());
                System.out.println("  Título de página: " + driver.getTitle());
                return;
            }

            System.out.println();
            System.out.printf("=== Primeros %d resultados para: \"%s\" ===%n", CANT_PRODUCTOS, PRODUCTO);
            System.out.println();

            int n = Math.min(CANT_PRODUCTOS, titulos.size());
            for (int i = 0; i < n; i++) {
                String texto = titulos.get(i).getText().trim();
                if (!texto.isEmpty()) {
                    System.out.printf("  %d. %s%n", i + 1, texto);
                }
            }

        } finally {
            driver.quit();
        }
        // Fuerza el cierre de hilos internos de Selenium que quedan activos
        // al correr dentro de la JVM de Maven con exec:java
        System.exit(0);
    }

    private static List<WebElement> resolverTitulos(WebDriver driver) {
        for (String selector : SELECTORES_TITULO) {
            List<WebElement> elementos = driver.findElements(By.cssSelector(selector));
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
