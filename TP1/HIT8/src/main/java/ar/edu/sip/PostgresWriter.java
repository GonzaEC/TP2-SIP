package ar.edu.sip;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Persiste resultados de scraping en PostgreSQL usando Flyway para migraciones. */
public class PostgresWriter {

  private static final Logger LOG = LoggerFactory.getLogger(PostgresWriter.class);

  private PostgresWriter() {}

  static String buildUrl() {
    String host = resolveEnv("POSTGRES_HOST", "localhost");
    String port = resolveEnv("POSTGRES_PORT", "5432");
    String db = resolveEnv("POSTGRES_DB", "scraper");
    return "jdbc:postgresql://" + host + ":" + port + "/" + db;
  }

  static String resolveEnv(String key, String defaultValue) {
    String val = System.getenv(key);
    return (val != null && !val.isBlank()) ? val.trim() : defaultValue;
  }

  static boolean isConfigured() {
    String host = System.getenv("POSTGRES_HOST");
    return host != null && !host.isBlank();
  }

  /**
   * Guarda los resultados de un producto en la base de datos. Si POSTGRES_HOST no está configurado,
   * omite silenciosamente.
   */
  public static void guardar(String producto, List<ProductResult> resultados, PriceStats stats) {
    if (!isConfigured()) {
      LOG.info("POSTGRES_HOST no configurado — omitiendo persistencia en BD.");
      return;
    }

    String url = buildUrl();
    String user = resolveEnv("POSTGRES_USER", "scraper");
    String password = resolveEnv("POSTGRES_PASSWORD", "");

    try {
      Flyway.configure().dataSource(url, user, password).load().migrate();

      try (Connection conn = DriverManager.getConnection(url, user, password)) {
        insertarResultados(conn, producto, resultados);
        insertarStats(conn, producto, stats);
        LOG.info("Guardados {} resultados para '{}'", resultados.size(), producto);
      }
    } catch (SQLException e) {
      LOG.error("Error al guardar en BD: {}", e.getMessage());
    }
  }

  private static void insertarResultados(
      Connection conn, String producto, List<ProductResult> resultados) throws SQLException {
    String sql =
        "INSERT INTO scrape_results"
            + " (producto, titulo, precio, link, tienda_oficial, envio_gratis, cuotas_sin_interes)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?)";

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      for (ProductResult r : resultados) {
        ps.setString(1, producto);
        ps.setString(2, r.getTitulo());
        if (r.getPrecio() != null) {
          ps.setLong(3, r.getPrecio());
        } else {
          ps.setNull(3, java.sql.Types.BIGINT);
        }
        ps.setString(4, r.getLink());
        ps.setString(5, r.getTiendaOficial());
        ps.setBoolean(6, r.isEnvioGratis());
        ps.setString(7, r.getCuotasSinInteres());
        ps.addBatch();
      }
      ps.executeBatch();
    }
  }

  private static void insertarStats(Connection conn, String producto, PriceStats stats)
      throws SQLException {
    if (stats.getTotal() == 0) {
      return;
    }
    String sql =
        "INSERT INTO price_stats"
            + " (producto, precio_min, precio_max, precio_mediana, precio_desviacion, total_con_precio)"
            + " VALUES (?, ?, ?, ?, ?, ?)";

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, producto);
      ps.setLong(2, stats.getMin());
      ps.setLong(3, stats.getMax());
      ps.setDouble(4, stats.getMediana());
      ps.setDouble(5, stats.getDesviacion());
      ps.setInt(6, stats.getTotal());
      ps.executeUpdate();
    }
  }
}
