package ar.edu.sip;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

import java.util.List;

public class BrowserFactory {

    private BrowserFactory() {}

    public static WebDriver create(String browserName) {
        String nombre = resolveName(browserName);
        System.out.println("[BrowserFactory] Navegador seleccionado: " + nombre);
        return switch (nombre.toLowerCase()) {
            case "chrome"  -> buildChrome();
            case "firefox" -> buildFirefox();
            default -> throw new IllegalArgumentException(
                "Navegador no soportado: \"" + nombre + "\". Valores válidos: chrome, firefox");
        };
    }

    public static WebDriver create() {
        return create(null);
    }

    public static String resolveName(String explicit) {
        if (explicit != null && !explicit.isBlank()) return explicit.trim();
        String prop = System.getProperty("browser");
        if (prop != null && !prop.isBlank()) return prop.trim();
        String env = System.getenv("BROWSER");
        if (env != null && !env.isBlank()) return env.trim();
        return "chrome";
    }

    private static WebDriver buildChrome() {
        ChromeOptions opts = new ChromeOptions();
        opts.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        opts.addArguments("--disable-blink-features=AutomationControlled");
        return new ChromeDriver(opts);
    }

    private static WebDriver buildFirefox() {
        FirefoxOptions opts = new FirefoxOptions();
        opts.addPreference("dom.webdriver.enabled", false);
        opts.addPreference("useAutomationExtension", false);
        return new FirefoxDriver(opts);
    }
}
