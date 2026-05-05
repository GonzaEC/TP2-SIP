# HIT #1 — Deploy del ECK Operator + Elasticsearch + Kibana

## Objetivo

Desplegar el **ECK Operator** oficial para gestionar el ciclo de vida de **Elasticsearch** y **Kibana** mediante CRDs en un namespace dedicado (`elastic`). El objetivo es dejar el backend de EFK operativo y accesible.

## Qué se hizo

### 1. Namespaces y ECK Operator

Se instaló el operador en un namespace separado para aislamiento operativo.

```bash
kubectl create namespace elastic
kubectl create namespace elastic-system
helm repo add elastic https://helm.elastic.co
helm install eck-operator elastic/eck-operator --version 2.16.0 --namespace elastic-system
```

### 2. Elasticsearch single-node (Custom Resource)

Se eligió el modo **single-node** debido a las restricciones de RAM de un cluster local k3s. ECK gestiona automáticamente los certificados internos (TLS) y el usuario `elastic`.

**Archivo**: `efk/manifests/elasticsearch.yaml`

Configuración clave:
- `node.store.allow_mmap: false` — necesario en k3s local-path.
- `ES_JAVA_OPTS: "-Xms1g -Xmx1g"` — heap fijo de 1GB (50% del limit de 2GB).
- `index.number_of_replicas: 0` — ineludible en single-node para que el status sea `green`.
- Resources: 1Gi-2Gi RAM, 500m-1000m CPU.

### 3. Kibana (Custom Resource)

**Archivo**: `efk/manifests/kibana.yaml` y `efk/manifests/kibana-nodeport.yaml`

- Conectado automáticamente a Elasticsearch vía `elasticsearchRef`.
- `service.type: NodePort` en puerto `30001`.
- Resources: 512Mi-1Gi RAM, 200m-500m CPU.

## Cómo levantar

```bash
cd efk && ./install.sh
```

## Verificación

```bash
# Status de los CRDs
kubectl -n elastic get elasticsearch scraper
kubectl -n elastic get kibana scraper

# Output esperado (HEALTH=green PHASE=Ready):
# NAME      HEALTH   NODES   VERSION   PHASE   AGE
# scraper   green    1       8.17.3    Ready   5m

# Pods corriendo
kubectl -n elastic get pods
```

## Acceso a Kibana

1. Recuperar password:
```bash
kubectl -n elastic get secret scraper-es-elastic-user -o jsonpath='{.data.elastic}' | base64 -d
```
2. Abrir `https://<NODE_IP>:30001` (Aceptar cert auto-firmado).

## Validación end-to-end

Al abrir Kibana, el status del cluster debe reportar 1 nodo conectado y saludable. Sin datos todavía (eso es el Hit #2).

## Versiones pinneadas

| Componente | Chart / CRD | Versión |
|---|---|---|
| ECK Operator | `elastic/eck-operator` | 2.16.0 |
| Elasticsearch | `elasticsearch.k8s.elastic.co/v1` | 8.17.3 |
| Kibana | `kibana.k8s.elastic.co/v1` | 8.17.3 |

## Archivos relevantes

| Archivo | Descripción |
|---|---|
| `efk/helm/eck-operator-values.yaml` | Recursos del operator |
| `efk/manifests/elasticsearch.yaml` | CR del cluster ES |
| `efk/manifests/kibana.yaml` | CR de la UI Kibana |
| `efk/manifests/kibana-nodeport.yaml` | Servicio de acceso externo |
| `efk/install.sh` | Script automatizado |
