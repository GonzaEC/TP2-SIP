package ar.edu.sip;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.ResourceAttributes;
import java.util.concurrent.TimeUnit;

/**
 * TP 2 - PARTE 3 - HIT 5 / HIT 6 — Inicialización del SDK de OpenTelemetry.
 *
 * <p>Configura:
 *
 * <ul>
 *   <li>LoggerProvider con exporter OTLP/gRPC → collector DaemonSet del nodo.
 *   <li>TracerProvider con exporter OTLP/gRPC → mismo endpoint (HIT 6).
 *   <li>Bridge Logback → OTel: todos los LOG.info/warn/error existentes se reenvían automáticamente
 *       sin cambiar MercadoLibreScraper.java.
 * </ul>
 *
 * <p>La variable de entorno {@code OTEL_EXPORTER_OTLP_ENDPOINT} es inyectada por el ConfigMap
 * {@code otel-collector-endpoint} (ver scraper-otlp-config.yaml). Default: {@code
 * http://localhost:4317} (útil para desarrollo local).
 */
public final class OtelSetup {

  private OtelSetup() {}

  private static OpenTelemetrySdk instance;

  /**
   * Inicializa el SDK y registra el shutdown hook para vaciar el buffer al salir. Debe llamarse UNA
   * SOLA VEZ al inicio de main(), antes de cualquier LOG.xxx().
   *
   * @return instancia de OpenTelemetry lista para crear Tracers (HIT 6).
   */
  public static OpenTelemetry init() {
    String endpoint =
        System.getenv().getOrDefault("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4317");

    String serviceName = System.getenv().getOrDefault("OTEL_SERVICE_NAME", "scraper");

    String appVersion = System.getenv().getOrDefault("APP_VERSION", "dev");

    String environment = System.getenv().getOrDefault("ENV", "tp");

    // Resource: metadata adjunta a todos los logs y spans
    Resource resource =
        Resource.getDefault()
            .merge(
                Resource.create(
                    Attributes.of(
                        ResourceAttributes.SERVICE_NAME, serviceName,
                        ResourceAttributes.SERVICE_VERSION, appVersion,
                        ResourceAttributes.DEPLOYMENT_ENVIRONMENT, environment)));

    // === Logs ===
    OtlpGrpcLogRecordExporter logExporter =
        OtlpGrpcLogRecordExporter.builder()
            .setEndpoint(endpoint)
            // k3d no tiene TLS en el collector, usamos plaintext
            .build();

    SdkLoggerProvider loggerProvider =
        SdkLoggerProvider.builder()
            .setResource(resource)
            .addLogRecordProcessor(BatchLogRecordProcessor.builder(logExporter).build())
            .build();

    // === Traces (HIT 6) ===
    OtlpGrpcSpanExporter spanExporter =
        OtlpGrpcSpanExporter.builder().setEndpoint(endpoint).build();

    SdkTracerProvider tracerProvider =
        SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
            .build();

    instance =
        OpenTelemetrySdk.builder()
            .setLoggerProvider(loggerProvider)
            .setTracerProvider(tracerProvider)
            .buildAndRegisterGlobal();

    // Bridge Logback → OTel SDK.
    // El OpenTelemetryAppender declarado en logback.xml necesita este install()
    // para saber a qué SDK enviar. Sin esto el appender queda sordo.
    OpenTelemetryAppender.install(instance);

    // Shutdown hook: vacía el BatchLogRecordProcessor antes de que el JVM muera.
    // Crítico para Jobs/CronJobs que terminan en segundos — sin esto los últimos
    // logs quedan en el buffer y nunca llegan al collector.
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  loggerProvider.shutdown().join(5, TimeUnit.SECONDS);
                  tracerProvider.shutdown().join(5, TimeUnit.SECONDS);
                },
                "otel-shutdown"));

    return instance;
  }

  /** Devuelve la instancia ya inicializada. Lanza ISE si init() no fue llamado antes. */
  public static OpenTelemetry get() {
    if (instance == null) {
      throw new IllegalStateException("OtelSetup.init() no fue llamado");
    }
    return instance;
  }
}
