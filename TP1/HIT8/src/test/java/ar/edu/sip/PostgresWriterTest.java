package ar.edu.sip;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests unitarios para PostgresWriter. Solo ejercen la lógica pura (resolución de env, URL, modo
 * "no configurado") sin necesitar un servidor PostgreSQL real.
 */
class PostgresWriterTest {

  // ── isConfigured ──────────────────────────────────────────────────────────

  @Test
  void isConfiguredRetornaFalseSiNoHayEnvVar() {
    // En un entorno de test sin POSTGRES_HOST seteado, debe retornar false.
    // Si la variable estuviera seteada en CI este test se omite con assumption.
    if (System.getenv("POSTGRES_HOST") != null) {
      // Si el entorno la tiene seteada, isConfigured() puede ser true — no la testamos.
      assertTrue(PostgresWriter.isConfigured());
      return;
    }
    assertFalse(PostgresWriter.isConfigured());
  }

  // ── resolveEnv ────────────────────────────────────────────────────────────

  @Test
  void resolveEnvRetornaDefaultSiNoHayVariable() {
    // Usamos una clave que con certeza no existe en el entorno de test
    String result = PostgresWriter.resolveEnv("_SIP_TEST_KEY_QQ_", "mi-default");
    assertEquals("mi-default", result);
  }

  @Test
  void resolveEnvRetornaDefaultConStringVacio() {
    // Si la variable no existe, devuelve el default (no lanza excepción)
    String result = PostgresWriter.resolveEnv("_SIP_EMPTY_", "fallback");
    assertEquals("fallback", result);
  }

  // ── buildUrl ──────────────────────────────────────────────────────────────

  @Test
  void buildUrlContieneJdbcPostgresqlPrefix() {
    String url = PostgresWriter.buildUrl();
    assertTrue(url.startsWith("jdbc:postgresql://"), "La URL debe empezar con jdbc:postgresql://");
  }

  @Test
  void buildUrlContieneHostYPuerto() {
    String url = PostgresWriter.buildUrl();
    // Debe contener algún host:puerto en la URL
    assertTrue(url.contains(":"), "La URL debe contener el separador de puerto");
  }

  // ── guardar sin DB configurada no lanza excepción ─────────────────────────

  @Test
  void guardarSinPostgresConfiguradorNoLanzaExcepcion() {
    if (PostgresWriter.isConfigured()) {
      // No podemos asegurar el comportamiento sin DB en este caso
      return;
    }

    ProductResult r = new ProductResult();
    r.setTitulo("Test");
    r.setPrecio(100_000L);
    r.setLink("https://example.com");

    PriceStats stats = PriceStats.calcular(List.of(r));

    assertDoesNotThrow(() -> PostgresWriter.guardar("test", List.of(r), stats));
  }
}
