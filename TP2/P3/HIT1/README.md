# HIT 1 — Deploy del OpenTelemetry Operator

El objetivo de este hit es instalar el **OpenTelemetry Operator**, el componente encargado de gestionar el ciclo de vida de los collectors y la auto-instrumentación en el cluster.

## Pasos Previos Requeridos

Antes de instalar el operador, es necesario asegurar que el cluster cuente con las siguientes dependencias:

1.  **cert-manager**: El operador utiliza webhooks de admisión para validar los recursos de OTel. Estos webhooks requieren TLS, y `cert-manager` es el estándar en Kubernetes para proveer estos certificados automáticamente.
    *   Instalación: `helm install cert-manager jetstack/cert-manager --namespace cert-manager --create-namespace --set installCRDs=true`
2.  **Namespaces**: Se crearon los namespaces `otel` (para los collectors) y `otel-operator-system` (para el manager del operador).
    *   Archivo: `otel/manifests/namespace.yaml`

## Implementación

La instalación se realizó utilizando Helm para una gestión declarativa:

1.  **Repo Helm**: `https://open-telemetry.github.io/opentelemetry-helm-charts`
2.  **Values personalizados**: Se configuraron límites de recursos y se especificó la imagen del collector para el manager.
    *   Archivo: `otel/helm/otel-operator-values.yaml`
3.  **Comando**:
    ```bash
    helm upgrade --install otel-operator open-telemetry/opentelemetry-operator \
      --version 0.74.0 \
      --namespace otel-operator-system \
      --values otel/helm/otel-operator-values.yaml
    ```

## Verificación

Para confirmar que el operador está listo:

```bash
kubectl -n otel-operator-system get pods
# Esperado: otel-operator-controller-manager en estado Running (2/2 containers)

kubectl get crds | grep opentelemetry
# Esperado: aparicion de opentelemetrycollectors e instrumentations
```
