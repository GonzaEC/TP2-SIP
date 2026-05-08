# Decision Matrix: Observability Stack por Contexto

> **Tesis central:** No existe "el mejor stack" en abstracto. Existe "el mejor stack para X equipo, X presupuesto, X regulación, X madurez operativa". Esta matriz defiende esa idea celda por celda.

---

## Cómo leer esta matriz

Cada celda contiene tres elementos:

- **Veredicto:** ✅ Recomendado · ⚠️ Aceptable · ❌ Desaconsejado
- **Razón principal** (≤ 25 palabras): el argumento decisivo para este contexto.
- **Caveat** (≤ 20 palabras): el "pero ojo con esto" que cambiaría el veredicto.

---

## La Matriz

| Contexto | Loki + Promtail/Alloy + Grafana | EFK (Elasticsearch + Fluentd/Fluent Bit + Kibana) | OpenTelemetry Collector | Datadog (SaaS managed) | Splunk (on-prem / Cloud) |
|---|---|---|---|---|---|
| **1. Startup OSS-only** | ✅ | ⚠️ | ✅ | ❌ | ❌ |
| **2. Mid Enterprise** | ✅ | ⚠️ | ✅ | ⚠️ | ⚠️ |
| **3. Regulated (banca/salud)** | ⚠️ | ⚠️ | ✅ | ❌ | ✅ |
| **4. Edge / IoT** | ❌ | ❌ | ✅ | ❌ | ❌ |
| **5. Cloud-native multi-region** | ⚠️ | ❌ | ✅ | ✅ | ⚠️ |

---

## Detalle de cada celda

### 1. Startup OSS-only
*3–5 devs · presupuesto cloud < USD 200/mes · sin equipo de plataforma dedicado*

---

#### Startup × Loki + Promtail/Alloy + Grafana
✅ **Recomendado.**
Costo $0, opera 1 persona part-time, escala fácil hasta cientos de GB/día.
**Caveat:** Si crecen a full-text search intensivo, migrar a OTel-frontend antes para no re-instrumentar.

---

#### Startup × EFK
⚠️ **Aceptable.**
Elasticsearch consume ≥ 4 GB RAM de entrada; en cloud < $200/mes devora el presupuesto entero.
**Caveat:** Viable solo con cluster single-node en instancia spot y disciplina extrema de retención.

---

#### Startup × OpenTelemetry Collector
✅ **Recomendado.**
Actúa como capa de vendor-neutralidad; envía a Loki/Prometheus sin costo adicional y evita lock-in.
**Caveat:** OTel Collector no es un backend: necesita destino final (Loki, Tempo, etc.) para cerrar el stack.

---

#### Startup × Datadog
❌ **Desaconsejado.**
Facturación por host + logs indexados supera $200/mes con solo 3 hosts en semanas.
**Caveat:** Si consiguen créditos de startup (Datadog for Startups), re-evaluar temporalmente; no es solución sostenible.

---

#### Startup × Splunk
❌ **Desaconsejado.**
Licencia basada en ingesta (GB/día) tiene piso mínimo que excede presupuesto total del equipo.
**Caveat:** Splunk Cloud free tier existe pero limita a 500 MB/día; inviable como stack de producción real.

---

### 2. Mid Enterprise
*50–200 devs · presupuesto OK pero finito · equipo de plataforma 2–4 personas · Grafana ya en producción para métricas*

---

#### Mid Enterprise × Loki + Promtail/Alloy + Grafana
✅ **Recomendado.**
Grafana ya está en producción; agregar Loki es un datasource más, reutiliza skills y dashboards existentes.
**Caveat:** A escala de 200 devs con retención larga, Loki requiere tuning de chunk y compactor para no degradarse.

---

#### Mid Enterprise × EFK
⚠️ **Aceptable.**
Elasticsearch ofrece full-text search superior y ML nativo (Elastic SIEM); justificable si ya hay expertise interno.
**Caveat:** El equipo de plataforma de 2–4 personas sufrirá con cluster management de ES a esa escala sin dedicación.

---

#### Mid Enterprise × OpenTelemetry Collector
✅ **Recomendado.**
Con 50–200 devs y múltiples lenguajes, OTel unifica instrumentación; el equipo de plataforma controla el pipeline centralmente.
**Caveat:** Adopción requiere evangelización interna; sin buy-in de dev leads, los equipos seguirán con SDKs propietarios.

---

#### Mid Enterprise × Datadog
⚠️ **Aceptable.**
Time-to-value alto; funcionalidades como APM correlacionado y anomaly detection aceleran al equipo de plataforma pequeño.
**Caveat:** Facturación por host escala mal con 200 devs; negociar contrato enterprise antes de comprometerse o la factura explota.

---

#### Mid Enterprise × Splunk
⚠️ **Aceptable.**
Si el área de seguridad ya usa Splunk SIEM, converger logs de aplicación ahorra duplicar pipelines y licencias.
**Caveat:** Splunk requiere Splunk admin dedicado; el equipo de plataforma de 2–4 personas lo absorbe difícilmente sin formación.

---

### 3. Regulated (banca / salud)
*Retención ≥ 7 años · audit trail · encriptación en reposo y tránsito · cumplimiento BCRA/HIPAA/ISO 27001 · DPA firmado para cualquier SaaS*

---

#### Regulated × Loki + Promtail/Alloy + Grafana
⚠️ **Aceptable.**
Grafana Labs ofrece Grafana Enterprise con RBAC granular y audit log; cumple HIPAA si se despliega on-prem o en VPC dedicada.
**Caveat:** El cifrado en reposo de objetos S3/GCS debe configurarse explícitamente; Loki no lo activa por defecto.

---

#### Regulated × EFK
⚠️ **Aceptable.**
Elasticsearch con Security habilitado (TLS, field-level security, audit logging) tiene certificación SOC 2 y puede cumplir HIPAA.
**Caveat:** Elastic Stack requiere licencia Platinum/Enterprise para audit log completo; la versión OSS gratuita no cumple regulación.

---

#### Regulated × OpenTelemetry Collector
✅ **Recomendado.**
OTel Collector como capa intermedia permite enmascarar PII en pipeline antes de persistir; reduce superficie regulatoria.
**Caveat:** OTel no almacena datos; el backend final (Loki, ES, Splunk) debe cumplir el estándar regulatorio completo.

---

#### Regulated × Datadog
❌ **Desaconsejado.**
Datadog SaaS envía datos a infraestructura Datadog Inc.; sin DPA firmado y revisión legal, viola BCRA/HIPAA inmediatamente.
**Caveat:** Datadog tiene DPA disponible y puede cumplir HIPAA con BAA; el proceso legal tarda meses y no siempre cierra.

---

#### Regulated × Splunk
✅ **Recomendado.**
Splunk Enterprise on-prem cumple retención de 7 años, cifrado AES-256, audit trail nativo y está en múltiples certificaciones.
**Caveat:** Costo total de propiedad es el más alto de la matriz; requiere Splunk admin certificado y hardware dedicado.

---

### 4. Edge / IoT
*Agente en dispositivos con 256 MB RAM · conectividad intermitente · batched ingestion · sin cluster local · solo collector embebido*

---

#### Edge/IoT × Loki + Promtail/Alloy + Grafana
❌ **Desaconsejado.**
Promtail/Alloy tiene footprint razonable pero Loki requiere backend centralizado; sin conectividad estable, se pierden logs.
**Caveat:** Alloy con modo "file-based buffer" mitiga pérdida parcialmente, pero no fue diseñado para conectividad intermitente real.

---

#### Edge/IoT × EFK
❌ **Desaconsejado.**
Fluentd consume ≥ 40 MB RAM solo; Elasticsearch no tiene modo embebido; la arquitectura completa es incompatible con 256 MB.
**Caveat:** Fluent Bit (no Fluentd) cabe en 650 KB RAM; puede forward a EFK remoto, pero el backend central sigue sin resolver.

---

#### Edge/IoT × OpenTelemetry Collector
✅ **Recomendado.**
OTel Collector en modo "gateway" con buffer persistente en disco + batching maneja conectividad intermitente nativamente.
**Caveat:** Configurar retry/backoff y buffer en disco requiere conocer bien el Collector; la documentación de edge es aún madura.

---

#### Edge/IoT × Datadog
❌ **Desaconsejado.**
El Datadog Agent consume ~250 MB RAM mínimo; igual al límite total del dispositivo, sin margen para la aplicación.
**Caveat:** Datadog tiene una versión IoT Agent experimental; aún en beta y sin SLA de producción documentado.

---

#### Edge/IoT × Splunk
❌ **Desaconsejado.**
Splunk Universal Forwarder necesita ~100 MB RAM y conectividad TCP estable; ambos supuestos se violan en este contexto.
**Caveat:** Splunk Edge Processor existe en roadmap pero no reemplaza aún al forwarder embebido para casos de RAM limitada.

---

### 5. Cloud-native multi-region
*Aplicación en 3+ regiones AWS/GCP · latencia inter-región importa · federation de queries necesaria · equipo SRE 5+ personas*

---

#### Cloud-native × Loki + Promtail/Alloy + Grafana
⚠️ **Aceptable.**
Loki soporta modo multi-tenant y Grafana tiene federation nativa vía Grafana Enterprise Metrics/Logs; el SRE team puede operarlo.
**Caveat:** Federation de queries en Loki es más limitada que en Thanos/Cortex para métricas; queries cross-region tienen latencia notable.

---

#### Cloud-native × EFK
❌ **Desaconsejado.**
Elasticsearch cross-cluster search (CCS) en 3+ regiones añade latencia y complejidad operativa desproporcionada para logs.
**Caveat:** Si ya tienen ES en producción y el SRE team domina CCS + CCR, puede mantenerse; migrar solo por multi-region no justifica el costo.

---

#### Cloud-native × OpenTelemetry Collector
✅ **Recomendado.**
OTel Collector en modo gateway por región + tail-sampling centralizado es el patrón estándar para multi-region SRE.
**Caveat:** Diseñar el pipeline de sampling correcto requiere entender tráfico por región; configuración incorrecta pierde trazas críticas.

---

#### Cloud-native × Datadog
✅ **Recomendado.**
Datadog es multi-region nativo; federation, APM correlacionado y SLO tracking funcionan out-of-the-box para equipos SRE maduros.
**Caveat:** A escala de 3+ regiones con alto volumen, el costo mensual puede superar el de un equipo dedicado de infraestructura.

---

#### Cloud-native × Splunk
⚠️ **Aceptable.**
Splunk Cloud con SmartStore en S3 puede operar multi-region, pero el modelo de federation no es tan maduro como Datadog.
**Caveat:** Splunk no tiene federation de queries inter-región out-of-the-box; requiere arquitectura custom con indexer clustering.

---

## Resumen ejecutivo: patrones que emergen de la matriz

### Cuándo Loki + Grafana gana
Entornos donde Grafana ya existe (mid enterprise), el presupuesto es limitado (startup), o el equipo prefiere operar su propia infraestructura sin lock-in en un vendor de logs.

### Cuándo EFK tiene sentido
Únicamente cuando existe expertise interno establecido con Elasticsearch, o cuando el caso de uso requiere full-text search complejo con queries analíticas que Loki no soporta. No justifica su peso operativo en entornos nuevos.

### Cuándo OpenTelemetry Collector es la respuesta correcta
Siempre como capa de abstracción. OTel no compite con los backends: los complementa. Es la respuesta correcta en edge/IoT, en regulated (para anonimizar PII en tránsito), y en multi-region (para sampling inteligente). Aparece como ✅ en 4 de los 5 contextos precisamente porque su rol es ortogonal al del backend.

### Cuándo Datadog justifica su costo
Solo cuando el equipo tiene presupuesto claro, el time-to-value importa más que el control operativo, y el volumen de datos no dispara la facturación. Nunca en regulated sin DPA firmado, nunca en edge/IoT, nunca en startup sin créditos.

### Cuándo Splunk es inevitable
En contextos regulados con requisitos de retención larga y audit trail donde el área de seguridad ya lo opera como SIEM. Fuera de ese nicho, su TCO no se justifica frente a alternativas OSS maduras.

---

## Antipatrones frecuentes que esta matriz penaliza

| Antipatrón | Qué celda lo expone | Por qué es un error |
|---|---|---|
| "Usemos Elasticsearch porque es el estándar" | Startup × EFK, Edge × EFK | ES consume recursos que esos contextos no tienen |
| "Datadog para todo, es fácil" | Regulated × Datadog, Edge × Datadog | Fácil ≠ correcto; en regulated viola compliance y en edge no cabe |
| "OTel reemplaza el backend" | Todas × OTel | OTel es pipeline, no storage; siempre necesita un backend final |
| "Splunk on-prem en startup para ser serios" | Startup × Splunk | Overhead de licencia y administración destruye velocity en equipos chicos |
| "Loki en edge porque es liviano" | Edge × Loki | Loki necesita backend centralizado; sin conectividad estable, falla |

---

*Última actualización: ver historial de commits del repositorio.*