package ar.edu.sip;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calcula estadísticas de precios (mín, máx, mediana, desvío estándar) para un conjunto de
 * resultados.
 */
public class PriceStats {

  private static final Logger LOG = LoggerFactory.getLogger(PriceStats.class);

  private final long min;
  private final long max;
  private final double mediana;
  private final double desviacion;
  private final int total;

  private PriceStats(long min, long max, double mediana, double desviacion, int total) {
    this.min = min;
    this.max = max;
    this.mediana = mediana;
    this.desviacion = desviacion;
    this.total = total;
  }

  /** Calcula estadísticas a partir de los precios no nulos de la lista. */
  public static PriceStats calcular(List<ProductResult> resultados) {
    List<Long> precios =
        resultados.stream()
            .map(ProductResult::getPrecio)
            .filter(Objects::nonNull)
            .filter(p -> p > 0)
            .sorted()
            .collect(Collectors.toList());

    if (precios.isEmpty()) {
      return new PriceStats(0, 0, 0.0, 0.0, 0);
    }

    long min = precios.get(0);
    long max = precios.get(precios.size() - 1);
    double mediana = calcularMediana(precios);
    double desviacion = calcularDesviacion(precios);

    return new PriceStats(min, max, mediana, desviacion, precios.size());
  }

  static double calcularMediana(List<Long> precios) {
    int n = precios.size();
    if (n == 0) {
      return 0.0;
    }
    if (n % 2 == 1) {
      return precios.get(n / 2);
    }
    return (precios.get(n / 2 - 1) + precios.get(n / 2)) / 2.0;
  }

  static double calcularDesviacion(List<Long> precios) {
    if (precios.size() < 2) {
      return 0.0;
    }
    double media = precios.stream().mapToLong(Long::longValue).average().orElse(0.0);
    double varianza =
        precios.stream().mapToDouble(p -> Math.pow(p - media, 2)).sum() / precios.size();
    return Math.sqrt(varianza);
  }

  /** Imprime una línea de resumen con las estadísticas del producto. */
  public void imprimirResumen(String producto) {
    if (total == 0) {
      LOG.info(String.format("  %-30s | sin precios disponibles", producto));
      return;
    }
    LOG.info(
        String.format(
            "  %-30s | min=%,d | max=%,d | mediana=%,.0f | σ=%,.0f | n=%d",
            producto, min, max, mediana, desviacion, total));
  }

  public long getMin() {
    return min;
  }

  public long getMax() {
    return max;
  }

  public double getMediana() {
    return mediana;
  }

  public double getDesviacion() {
    return desviacion;
  }

  public int getTotal() {
    return total;
  }
}
