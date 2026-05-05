$NAMESPACE = "observability"

if (-not $env:GRAFANA_ADMIN_PASSWORD) {
    Write-Error "Por favor configura la variable GRAFANA_ADMIN_PASSWORD antes de ejecutar el script. Ejemplo: `$env:GRAFANA_ADMIN_PASSWORD='admin'"
    exit 1
}

$DIR = $PSScriptRoot

Write-Host "-- Namespace + Helm repo"
kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f -
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update

Write-Host "-- Secret de admin de Grafana"
kubectl -n $NAMESPACE create secret generic grafana-admin --from-literal=admin-user=admin --from-literal=admin-password="$env:GRAFANA_ADMIN_PASSWORD" --dry-run=client -o yaml | kubectl apply -f -

if (-not $env:DISCORD_WEBHOOK_URL) {
    Write-Warning "Variable DISCORD_WEBHOOK_URL no configurada. Las alertas de Grafana (HIT 6) se enviarán a un webhook dummy."
    $env:DISCORD_WEBHOOK_URL = "https://discord.com/api/webhooks/dummy/dummy"
}

Write-Host "-- Secret de alertas de Grafana (HIT 6)"
kubectl -n $NAMESPACE create secret generic grafana-alerts-secret --from-literal=discord-webhook-url="$env:DISCORD_WEBHOOK_URL" --dry-run=client -o yaml | kubectl apply -f -

Write-Host "-- Loki (single-binary, filesystem)"
helm upgrade --install loki grafana/loki --version 6.16.0 --namespace $NAMESPACE --values "$DIR\helm\loki-values.yaml" --wait --timeout 5m

Write-Host "-- Promtail (DaemonSet)"
helm upgrade --install promtail grafana/promtail --version 6.16.0 --namespace $NAMESPACE --values "$DIR\helm\promtail-values.yaml" --wait --timeout 3m

Write-Host "-- Dashboard ConfigMap"
kubectl -n $NAMESPACE create configmap scraper-overview-dashboard --from-file="scraper-overview.json=$DIR\dashboards\scraper-overview.json" --dry-run=client -o yaml | kubectl apply -f -

Write-Host "-- Alerting ConfigMap"
kubectl -n $NAMESPACE create configmap scraper-alerting --from-file="$DIR\manifests\alerting" --dry-run=client -o yaml | kubectl apply -f -

Write-Host "-- Grafana"
helm upgrade --install grafana grafana/grafana --version 8.5.0 --namespace $NAMESPACE --values "$DIR\helm\grafana-values.yaml" --wait --timeout 3m

Write-Host ""
Write-Host "OK Loki running"
Write-Host "OK Promtail running"
Write-Host "OK Grafana running"
Write-Host "OK Datasource Loki configurado"
Write-Host "OK Dashboard 'Scraper Overview' provisionado"
Write-Host "-- Para acceder, abre otro PowerShell y ejecuta: kubectl -n observability port-forward svc/grafana 30000:80"
Write-Host "-- Luego abre http://localhost:30000 (usuario: admin / password: $env:GRAFANA_ADMIN_PASSWORD)"
