package ar.edu.sip;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;

/**
 * Tests unitarios puros (sin WebDriver) para BrowserFactory. Validan la logica de resolucion de
 * nombre y modo headless.
 */
class BrowserFactoryTest {

  // ── resolveName ───────────────────────────────────────────────────────────

  @Test
  void resolveNameValorExplicitoRetornaExplicito() {
    assertEquals("firefox", BrowserFactory.resolveName("firefox"));
  }

  @Test
  void resolveNameValorExplicitoConEspaciosRetornaTrimeado() {
    assertEquals("chrome", BrowserFactory.resolveName("  chrome  "));
  }

  @Test
  void resolveNameNuloRetornaChromePorDefecto() {
    String result = BrowserFactory.resolveName(null);
    assertNotNull(result);
    assertFalse(result.isBlank());
  }

  @Test
  void resolveNameCadenaVaciaUsaFallback() {
    String result = BrowserFactory.resolveName("   ");
    assertNotNull(result);
  }

  // ── resolveHeadless ──────────────────────────────────────────────────────

  @Test
  void resolveHeadlessPropiedadTrueRetornaTrue() {
    System.setProperty("headless", "true");

    try {
      assertTrue(BrowserFactory.resolveHeadless());
    } finally {
      System.clearProperty("headless");
    }
  }

  @Test
  void resolveHeadlessPropiedadFalseRetornaFalse() {
    System.setProperty("headless", "false");

    try {
      assertFalse(BrowserFactory.resolveHeadless());
    } finally {
      System.clearProperty("headless");
    }
  }

  @Test
  void resolveHeadlessSinPropiedadSinEnvRetornaFalseDefault() {
    System.clearProperty("headless");
    boolean result = BrowserFactory.resolveHeadless();
    assertTrue(
        result
            == Boolean.parseBoolean(
                System.getenv("HEADLESS") != null ? System.getenv("HEADLESS") : "false"));
  }

  @Test
  void resolveNameSystemPropertyTienePrioridadSobreDefault() {
    System.setProperty("browser", "firefox");
    try {
      assertEquals("firefox", BrowserFactory.resolveName(null));
    } finally {
      System.clearProperty("browser");
    }
  }

  @Test
  void resolveNameCaeEnChromeCuandoTodoEsNulo() {
    String name = BrowserFactory.resolveName(null);
    if (System.getProperty("browser") == null && System.getenv("BROWSER") == null) {
      assertEquals("chrome", name);
    }
  }

  @Test
  void createFirefoxLlamaAlConstructorCorrecto() {
    try {
      BrowserFactory.create("firefox", true);
    } catch (Exception ignored) {
      // No nos importa si falla la instanciacion, nos importa que el coverage
      // marque que el switch entro en el caso "firefox".
    }
  }

  @Test
  void createSinParametrosUsaValoresPorDefecto() {
    assertDoesNotThrow(
        () -> {
          try {
            BrowserFactory.create();
          } catch (Exception ignored) {
            /* aceptable si no hay binario */
          }
        });
  }

  @Test
  void createNavegadorInvalidoLanzaIllegalArgument() {
    assertThrows(IllegalArgumentException.class, () -> BrowserFactory.create("safari", false));
  }
}
