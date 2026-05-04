package ar.edu.sip;

import java.util.List;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Crea instancias de WebDriver para Chrome o Firefox. */
public class BrowserFactory {

  private static final Logger LOG = LoggerFactory.getLogger(BrowserFactory.class);

  private BrowserFactory() {}

  private static final String CHROME_USER_AGENT =
      "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko)"
          + " Chrome/124.0.0.0 Safari/537.36";
  private static final String FIREFOX_USER_AGENT =
      "Mozilla/5.0 (X11; Linux x86_64; rv:125.0) Gecko/20100101 Firefox/125.0";

  public static WebDriver create(String browserName) {
    return create(browserName, resolveHeadless());
  }

  public static WebDriver create(String browserName, boolean headless) {
    String nombre = resolveName(browserName);
    LOG.info("Browser: {} | Headless: {}", nombre, headless);
    return switch (nombre.toLowerCase()) {
      case "chrome" -> buildChrome(headless);
      case "firefox" -> buildFirefox(headless);
      default ->
          throw new IllegalArgumentException(
              "Browser not supported: \"" + nombre + "\". Valid values: chrome, firefox");
    };
  }

  public static WebDriver create() {
    return create(null);
  }

  public static String resolveName(String explicit) {
    if (explicit != null && !explicit.isBlank()) {
      return explicit.trim();
    }

    String prop = System.getProperty("browser");

    if (prop != null && !prop.isBlank()) {
      return prop.trim();
    }

    String env = System.getenv("BROWSER");

    if (env != null && !env.isBlank()) {
      return env.trim();
    }
    return "chrome";
  }

  /**
   * Resuelve el modo headless. Prioridad: System property "headless" → variable de entorno HEADLESS
   * → false por defecto.
   */
  public static boolean resolveHeadless() {
    String property = System.getProperty("headless");

    if (property != null) {
      return Boolean.parseBoolean(property.trim());
    }

    String env = System.getenv("HEADLESS");
    if (env != null) {
      return Boolean.parseBoolean(env.trim());
    }

    return false;
  }

  private static WebDriver buildChrome(boolean headless) {
    ChromeOptions opts = new ChromeOptions();

    opts.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
    opts.addArguments("--disable-blink-features=AutomationControlled");
    opts.addArguments("--user-agent=" + CHROME_USER_AGENT);
    opts.addArguments(
        "--disable-gpu",
        "--disable-software-rasterizer",
        "--disable-extensions",
        "--disable-background-networking",
        "--disable-default-apps",
        "--no-first-run",
        "--window-size=1920,1080");

    if (headless) {
      opts.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage");
    }
    return new ChromeDriver(opts);
  }

  private static WebDriver buildFirefox(boolean headless) {
    FirefoxOptions opts = new FirefoxOptions();

    opts.addPreference("dom.webdriver.enabled", false);
    opts.addPreference("useAutomationExtension", false);
    opts.addPreference("general.useragent.override", FIREFOX_USER_AGENT);
    opts.addPreference("privacy.trackingprotection.enabled", false);
    opts.addPreference("browser.download.start_downloads_in_tmp_dir", true);

    if (headless) {
      opts.addArguments("--headless", "--width=1920", "--height=1080");
    }
    return new FirefoxDriver(opts);
  }
}
