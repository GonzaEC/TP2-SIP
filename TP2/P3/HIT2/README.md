# HIT 2 — OpenTelemetryCollector en modo Agent (DaemonSet)

En este hit se desplegó el **OTel Collector** actuando como agente de recolección en cada nodo del cluster. Su función inicial es leer logs del sistema de archivos y procesarlos.

## Pasos Previos Requeridos

1.  **Hit #1 Completado**: El operador debe estar funcionando para poder interpretar el CRD `OpenTelemetryCollector`.
2.  **RBAC Configurado**: El collector necesita permisos para consultar la API de Kubernetes y enriquecer los logs con metadata de los Pods (namespaces, nombres de pods, labels).
    *   Archivo: `otel/manifests/rbac.yaml`

## Implementación

Se aplicó un recurso de tipo `OpenTelemetryCollector` con la siguiente configuración:

1.  **Modo**: `daemonset` (un Pod por cada nodo).
2.  **Receiver `filelog`**: Configurado para leer los logs de los Pods en la ruta `/var/log/pods/ml-scraper_*/*/*.log`.
3.  **Procesador `k8sattributes`**: Enriquecimiento automático de los logs con información del cluster.
4.  **Exporter `debug`**: Utilizado para validar que el pipeline de datos funciona correctamente antes de conectar los backends finales.

Archivo de manifiesto: `otel/manifests/collector-agent.yaml`.

## Verificación

Para verificar que el agente está recolectando logs:

1.  **Estado del recurso**:
    ```bash
    kubectl -n otel get otelcol agent
    ```
2.  **Validación de logs**:
    Al ejecutar un Job del scraper, el collector debe mostrar en sus logs el procesamiento de las líneas de texto, incluyendo los atributos de Kubernetes:
    ```bash
    kubectl -n otel logs ds/agent-collector
    # Se debe observar la metadata enriquecida (k8s.pod.name, etc.)
    ```
