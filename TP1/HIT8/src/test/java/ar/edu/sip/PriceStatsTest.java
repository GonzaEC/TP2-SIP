package ar.edu.sip;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests unitarios para PriceStats: mín, máx, mediana y desvío estándar. */
class PriceStatsTest {

  private static ProductResult precio(long p) {
    ProductResult r = new ProductResult();
    r.setTitulo("X");
    r.setLink("https://example.com");
    r.setPrecio(p);
    return r;
  }

  private static ProductResult sinPrecio() {
    ProductResult r = new ProductResult();
    r.setTitulo("Sin precio");
    r.setLink("https://example.com");
    return r;
  }

  // ── Lista vacía ───────────────────────────────────────────────────────────

  @Test
  void calcularListaVaciaRetornaZeros() {
    PriceStats stats = PriceStats.calcular(Collections.emptyList());

    assertEquals(0, stats.getMin());
    assertEquals(0, stats.getMax());
    assertEquals(0.0, stats.getMediana());
    assertEquals(0.0, stats.getDesviacion());
    assertEquals(0, stats.getTotal());
  }

  @Test
  void calcularListaSoloPreciosNulosRetornaZeros() {
    PriceStats stats = PriceStats.calcular(List.of(sinPrecio(), sinPrecio()));

    assertEquals(0, stats.getTotal());
  }

  // ── Un solo elemento ─────────────────────────────────────────────────────

  @Test
  void calcularUnElementoRetornaMinMaxIguales() {
    PriceStats stats = PriceStats.calcular(List.of(precio(100_000L)));

    assertEquals(100_000L, stats.getMin());
    assertEquals(100_000L, stats.getMax());
    assertEquals(100_000.0, stats.getMediana());
    assertEquals(0.0, stats.getDesviacion(), 0.001);
    assertEquals(1, stats.getTotal());
  }

  // ── Mín y máx ────────────────────────────────────────────────────────────

  @Test
  void calcularMinMaxConVariosElementos() {
    List<ProductResult> items =
        List.of(precio(300_000L), precio(100_000L), precio(200_000L), precio(500_000L));

    PriceStats stats = PriceStats.calcular(items);

    assertEquals(100_000L, stats.getMin());
    assertEquals(500_000L, stats.getMax());
  }

  // ── Mediana ───────────────────────────────────────────────────────────────

  @Test
  void calcularMedianaConNumeroImparDeElementos() {
    // sorted: 1, 2, 3 → mediana = 2
    PriceStats stats = PriceStats.calcular(List.of(precio(3L), precio(1L), precio(2L)));

    assertEquals(2.0, stats.getMediana());
  }

  @Test
  void calcularMedianaConNumeroParDeElementos() {
    // sorted: 2, 4, 6, 8 → mediana = (4+6)/2 = 5
    PriceStats stats = PriceStats.calcular(List.of(precio(8L), precio(4L), precio(2L), precio(6L)));

    assertEquals(5.0, stats.getMediana());
  }

  @Test
  void calcularMedianaConDobleElemento() {
    // sorted: 10, 20 → mediana = (10+20)/2 = 15
    PriceStats stats = PriceStats.calcular(List.of(precio(10L), precio(20L)));

    assertEquals(15.0, stats.getMediana());
  }

  // ── Desvío estándar ───────────────────────────────────────────────────────

  @Test
  void calcularDesviacionTresValoresIgualesEsCero() {
    PriceStats stats = PriceStats.calcular(List.of(precio(100L), precio(100L), precio(100L)));

    assertEquals(0.0, stats.getDesviacion(), 0.001);
  }

  @Test
  void calcularDesviacionConDosValoresDistintos() {
    // valores: 4 y 6 → media=5, varianza=((4-5)²+(6-5)²)/2=1, σ=1
    PriceStats stats = PriceStats.calcular(List.of(precio(4L), precio(6L)));

    assertEquals(1.0, stats.getDesviacion(), 0.001);
  }

  // ── Helpers internos ──────────────────────────────────────────────────────

  @Test
  void calcularMedianaListaUnElemento() {
    assertEquals(42.0, PriceStats.calcularMediana(List.of(42L)));
  }

  @Test
  void calcularMedianaListaVaciaRetornaCero() {
    assertEquals(0.0, PriceStats.calcularMediana(Collections.emptyList()));
  }

  @Test
  void calcularDesviacionUnElementoRetornaCero() {
    assertEquals(0.0, PriceStats.calcularDesviacion(List.of(100L)), 0.001);
  }

  // ── Precios negativos/cero se ignoran ────────────────────────────────────

  @Test
  void calcularIgnoraPreciosCeroYNegativos() {
    List<ProductResult> items = List.of(precio(0L), precio(-100L), precio(200L), precio(300L));

    PriceStats stats = PriceStats.calcular(items);

    assertEquals(2, stats.getTotal(), "Solo deben contarse precios > 0");
    assertEquals(200L, stats.getMin());
    assertEquals(300L, stats.getMax());
  }

  // ── imprimirResumen no lanza excepciones ──────────────────────────────────

  @Test
  void imprimirResumenConDatosNoLanzaExcepcion() {
    PriceStats stats = PriceStats.calcular(List.of(precio(100L), precio(200L)));

    assertDoesNotThrow(() -> stats.imprimirResumen("Producto Test"));
  }

  @Test
  void imprimirResumenSinPreciosNoLanzaExcepcion() {
    PriceStats stats = PriceStats.calcular(Collections.emptyList());

    assertDoesNotThrow(() -> stats.imprimirResumen("Producto Vacío"));
  }
}
