# 0012 — Stack de observabilidad final para API pública de agregación de precios

- **Date:** 2026-06-01
- **Status:** Accepted
- **Deciders:** Equipo de 4 devs full-stack (sin SRE, sin platform engineer)
- **Supersedes:** 0007 (Loki + Promtail), 0009 (EFK) — ver §7

---

## 1. Contexto

Operamos una API pública de agregación de precios de e-commerce: un scraper distribuido que consulta catálogos de tiendas online argentinas y expone un endpoint REST de consulta para clientes externos. El sistema corre en EKS (AWS, región us-east-1) con GitHub Actions como único mecanismo de CI/CD.

**Equipo:** 4 desarrolladores full-stack. Sin SRE, sin platform engineer, sin oncall formal. La operación de infraestructura es una responsabilidad compartida y rotatoria entre el equipo de producto.

**Presupuesto cloud:** USD 280/mes total en AWS. No existe presupuesto separado para observabilidad; cualquier herramienta que se adopte compite con los nodos de compute y el almacenamiento de datos.

**Restricciones regulatorias:** Ninguna. Somos una startup pre-seed, no procesamos PII, no estamos sujetos a GDPR, PCI-DSS ni Ley 25.326.

**Volumen de logs:** ~300 MB/día en el estado actual (4 servicios: scraper, normalizer, API gateway, scheduler). Proyección conservadora a 3 GB/día en 12 meses, asumiendo que el catálogo de tiendas crece de 15 a ~150 y que incorporamos logging estructurado completo.

**Madurez operativa:** Tenemos `kubectl` para operaciones de emergencia y GitHub Actions para deploys. No tenemos dashboards de negocio, ni alertas, ni ninguna forma de correlacionar un error de usuario con un log de un servicio interno. El dolor operativo actual es real: debugging de incidentes requiere hacer `kubectl logs` en múltiples pods manualmente.

Este escenario es representativo del primer trabajo de un desarrollador backend en una startup de tecnología en Argentina: presupuesto estrecho, equipo pequeño sin especialistas de infra, y necesidad urgente de visibilidad operativa básica sin dedicar un sprint entero a configurar herramientas.

---

## 2. Alternativas consideradas

### 2.1. Loki + Promtail/Alloy + Grafana

Stack de logging nativo de Grafana Labs, diseñado bajo el principio "index labels, not text". Loki almacena logs comprimidos en object storage y solo indexa las etiquetas (labels) definidas explícitamente; las queries se hacen en LogQL.

**Mediciones empíricas (Hit #1):** RAM total del stack = 324 MiB; tiempo de deploy clean-to-first-log = 3:58 min; tamaño de imagen del agente (Promtail) = 76.4 MiB.

**Pro principal en nuestro contexto:** Footprint de recursos muy bajo para un equipo sin SRE. Integración nativa con Grafana (que ya usaríamos para métricas de Prometheus).

**Contra principal:** Loki es label-first; búsquedas de texto libre sobre campos arbitrarios son lentas comparadas con Elasticsearch. Si el equipo eventualmente necesita full-text search sobre payloads de error, Loki no es la herramienta adecuada.

---

### 2.2. EFK (Elasticsearch + Fluentd/Fluent Bit + Kibana)

Stack clásico de logging: Elasticsearch como motor de búsqueda e indexado, Fluent Bit como agente de recolección liviano, Kibana como interfaz de exploración y dashboards.

**Mediciones empíricas (Hit #1):** RAM total del stack = 2142 MiB; tiempo de deploy clean-to-first-log = 9:09 min; tamaño de imagen del agente (Fluent Bit) = 39.4 MiB.

**Pro principal:** Full-text search sobre cualquier campo del log sin configuración previa. Kibana es maduro y tiene capacidades avanzadas de visualización.

**Contra principal en nuestro contexto:** El consumo de RAM (2142 MiB) es 6.6× el de Loki (324 MiB). Para nuestro presupuesto de USD 280/mes, eso representa una instancia EC2 adicional de ~USD 35-50/mes dedicada exclusivamente a observabilidad — un 12-18% del presupuesto total. Con un equipo sin SRE, el costo operativo de tuning de Elasticsearch (heap sizing, shard management, index lifecycle policies) es otro impuesto invisible.

---

### 2.3. OpenTelemetry Collector + backend

OpenTelemetry Collector es un componente CNCF vendor-neutral que actúa como capa de instrumentación universal: recibe señales (logs, métricas, traces) de múltiples fuentes y las reenvía a cualquier backend compatible (Loki, Elasticsearch, Jaeger, Prometheus, etc.).

**Mediciones empíricas (Hit #1):** RAM del Collector aislado = 84 MiB; CPU = 7 mCPU; tiempo de deploy = 8:13 min. El Collector es passthrough: no almacena datos, por lo que el disco depende del backend elegido.

**Pro principal:** Desacopla la instrumentación del backend. Migrar de Loki a Elasticsearch en el futuro no requiere re-instrumentar los servicios; solo se cambia la configuración del exportador en el Collector.

**Contra principal:** Solo como componente aislado no es una solución de observabilidad completa. Necesita un backend para ser útil, lo que lo convierte en una capa adicional. Sin embargo, su bajo footprint (84 MiB) permite combinarlo con Loki sin que el total supere los 408 MiB — cifra manejable.

---

### 2.4. Datadog Logs (managed SaaS)

Plataforma de observabilidad managed con agente propietario, ingesta automática, correlación de logs-métricas-traces en una sola UI, y alertas ML-asistidas.

**No fue medida empíricamente.** El modelo de pricing de Datadog se basa en volumen ingerido y retención: aproximadamente USD 1.27/GB ingerido + USD 0.10/GB/mes de retención adicional. Para nuestra proyección de 3 GB/día en 12 meses, eso implicaría ~USD 114/mes solo en ingesta, más del 40% de nuestro presupuesto cloud total.

**Descartada por:** costo prohibitivo en nuestro contexto y vendor lock-in severo (el agente propietario no es intercambiable con ninguna alternativa open-source). La ergonomía es excelente y la plataforma es genuinamente poderosa; es la elección correcta para equipos con presupuesto y sin voluntad de operar infraestructura. Para nosotros, no aplica en este horizonte.

---

### 2.5. Splunk Cloud

Plataforma de SIEM y observabilidad enterprise con capacidades de búsqueda avanzada (SPL), correlación de eventos de seguridad y cumplimiento regulatorio.

**No fue medida empíricamente.** Splunk tiene un pricing más opaco que Datadog (basado en "workloads" e "ingest tiers"), pero consistentemente más caro en el extremo bajo del mercado. Su fortaleza diferencial respecto a Datadog es el componente de seguridad (SIEM) y el cumplimiento regulatorio (SOC 2, PCI-DSS, HIPAA).

**Descartada por:** precio, complejidad de licenciamiento, y porque sus fortalezas (SIEM, compliance) no son relevantes para nuestro escenario sin regulación. Mencionada como referencia porque es el estándar en empresas enterprise-regulated; si el escenario del §1 incluyera PCI-DSS, la evaluación sería distinta.

---

## 3. Decisión

**Adoptamos OpenTelemetry Collector como capa de instrumentación universal + Loki como backend de logs + Grafana como plataforma de visualización y alertas, con plan de evolución hacia Tempo (traces distribuidos) en los próximos 6 meses.**

Esta combinación gana en nuestro contexto por tres razones convergentes. Primero, el footprint total del stack (OTel Collector + Loki + Grafana) se mantiene por debajo de los 500 MiB de RAM, lo que permite operar en los nodos existentes sin provisionar instancias adicionales. EFK requiere 2142 MiB solo para el stack de logging, lo que es incompatible con nuestro presupuesto. Segundo, el tiempo de deploy clean-to-first-log de Loki (3:58 min) es menos de la mitad que EFK (9:09 min), lo que reduce el MTTR en escenarios de "borrón y reinstalo" por un factor de 2.3×. Tercero, instrumentar con OTel desde el inicio nos protege contra el lock-in futuro: si en 12 meses el volumen de logs justifica migrar a un backend más potente (Elasticsearch, ClickHouse, o incluso Datadog), los servicios no necesitan ser re-instrumentados.

EFK queda descartado por costo de recursos, no por calidad técnica. Datadog y Splunk quedan descartados por presupuesto. Loki standalone (sin OTel) queda descartado porque nos ata a Promtail como único agente posible.

Si el escenario del §1 cambia, esta decisión se revisita con los siguientes umbrales: (a) si el presupuesto cloud supera USD 800/mes, Datadog se vuelve competitivo; (b) si el equipo crece a 2+ SREs dedicados, el costo operativo de EFK deja de ser un bloqueante; (c) si aparece un requisito de compliance regulatorio (PCI, HIPAA), Splunk o Datadog pasan a ser candidatos serios.

---

## 4. Trade-offs aceptados explícitos

**Renunciamos a full-text search eficiente sobre contenido de logs.** Loki indexa labels, no texto libre. Buscar una subcadena arbitraria dentro del body de un log requiere filtros de línea (`|=`) que hacen un scan completo del período consultado. Para nuestro volumen actual (300 MB/día) esto es tolerable; si en 12 meses llegamos a 3 GB/día y necesitamos búsquedas ad-hoc frecuentes sobre payloads de error, el tiempo de query puede volverse inaceptable. Mitigación: definir desde el inicio un esquema de labels estructurado (`service`, `level`, `trace_id`) para minimizar la dependencia de full-text search. Si el problema se materializa, la capa OTel nos permite agregar un exportador a Elasticsearch en paralelo sin tocar los servicios.

**Aceptamos operar Loki en modo single-binary sin alta disponibilidad.** No tenemos HA. Si el pod de Loki muere y el PVC se corrompe, perdemos el historial de logs almacenado. Mitigación: snapshots semanales automatizados del PVC vía AWS EBS Snapshots (costo: ~USD 0.05/GB/mes), y los servicios siguen emitiendo a stdout, por lo que `kubectl logs` sigue siendo un fallback válido para los últimos minutos.

**Aceptamos la complejidad operativa adicional de OTel Collector como componente extra.** Agregar el Collector como componente intermediario es una pieza más que puede fallar. Un bug en el pipeline de OTel puede interrumpir la ingesta de todos los backends simultáneamente. Mitigación: pipeline de OTel con fallback a archivo local + configuración de buffer en disco para absorber interrupciones temporales del backend.

**Renunciamos a correlación nativa logs-métricas-traces en una sola UI.** Grafana permite correlacionar Loki (logs), Prometheus (métricas) y Tempo (traces) en dashboards mixtos, pero la integración es más manual que en Datadog o New Relic. En la práctica, para un equipo de 4 devs sin cultura de observabilidad establecida, esto es aceptable en el corto plazo. Mitigación: el plan de evolución a 6 meses (§6) incluye Tempo, lo que completa la triada logs-métricas-traces en Grafana.

**Asumimos deuda técnica en la configuración inicial de OTel.** La curva de aprendizaje de OTel Collector (pipelines, procesadores, exportadores) no es trivial. El primer deploy funcional puede tomar 1-2 días de un desarrollador. Mitigación: usar la distribución `otelcol-contrib` con configuración mínima de inicio (receiver OTLP + exporter Loki) y escalar la complejidad gradualmente.

---

## 5. Evidencia empírica (Hit #1)

Las siguientes mediciones fueron tomadas en un cluster de desarrollo local (k3s) con carga sintética equivalente a nuestro volumen de producción actual (~300 MB/día de logs). Los números de query latency y disco PVC no fueron medidos en este ciclo (marcado como N/M) y se documentan como deuda de medición para el siguiente sprint.

| Métrica | Loki + Promtail + Grafana | EFK (ES + Fluent Bit + Kibana) | OTel Collector |
|---|---|---|---|
| RAM total (MiB) | 324 | 2142 | 84 |
| CPU total (mCPU) | 30 | 81 | 7 |
| Disco PVC tras 24 h (MiB) | N/M | N/M | passthrough (0) |
| Query latency p50 (ms) | N/M | N/M | N/M |
| Query latency p95 (ms) | N/M | N/M | N/M |
| Deploy clean → first log | 3:58 min | 9:09 min | 8:13 min |
| Tamaño imagen agente (MiB) | 76.4 | 39.4 | 73.3 |

**Tres referencias clave al cuerpo de esta decisión:**

- El footprint de RAM de EFK (2142 MiB medido) es 6.6× el de Loki (324 MiB). Para nuestro cluster en AWS, eso representa una instancia `t3.medium` adicional (~USD 30/mes) dedicada exclusivamente a observabilidad — un 10.7% de nuestro presupuesto cloud total, que consideramos inaceptable sin un SRE que lo justifique operativamente.

- El tiempo de deploy clean-to-first-log de Loki (3:58 min) es 2.3× más rápido que EFK (9:09 min). En un escenario de "borrón total y reinstalo" durante un incidente a las 3 AM — que en un equipo sin oncall formal ocurre eventualmente — esa diferencia puede ser la diferencia entre 10 minutos y 25 minutos a ciegas.

- El OTel Collector con 84 MiB de RAM y 7 mCPU puede correr como sidecar o DaemonSet sin impacto medible en el presupuesto de recursos. Combinado con Loki (324 MiB), el stack total de observabilidad usa menos RAM que solo el stack de EFK.

---

## 6. Plan de evolución

### 6 meses

**Trigger:** El volumen de logs supera 1 GB/día sostenido O el equipo experimenta un incidente donde la ausencia de traces distribuidos alargó el debugging más de 45 minutos.

**Acción concreta:** Agregar Tempo como backend de traces al stack existente. Como ya estamos instrumentados con OTel, el cambio es agregar un exportador de traces en el Collector (`exporter: otlp/tempo`) y desplegar Tempo en el cluster. Los servicios no se tocan.

**Riesgo si no se hace:** A medida que los servicios crecen de 4 a 8+, correlacionar un error de usuario con el servicio causante mediante solo logs se vuelve una búsqueda manual por timestamps. El MTTR de incidentes complejos crece linealmente con la cantidad de servicios.

---

### 12 meses

**Trigger:** El volumen de logs supera 3 GB/día O el equipo crece a 6+ devs y aparece un rol de DevOps/SRE part-time.

**Acción concreta:** Evaluar migrar el storage de Loki de disco local (PVC) a S3 con Loki en modo "simple scalable deployment" (separar read/write path). Esto elimina el single point of failure del PVC y permite escalar el volumen sin provisionar más nodos.

**Riesgo si no se hace:** Con 3 GB/día de ingesta, el PVC local crece ~90 GB/mes. Los costos de EBS escalan linealmente y la operación manual de retención se vuelve un toil recurrente. Sin migrar a object storage, el equipo termina dedicando tiempo de ingeniería a garbage collection de logs en lugar de producto.

---

### 24 meses

**Trigger:** El equipo supera 10 devs, el presupuesto cloud supera USD 800/mes, o aparece un requisito de compliance (PCI, GDPR por expansión regional).

**Acción concreta:** Reevaluar el stack completo. En este horizonte, Datadog puede ser competitivo en términos de costo total de ownership (costo de plataforma vs. costo de operar infraestructura propia con un equipo más grande). Alternativamente, si el requisito es compliance, evaluar Splunk Cloud o una solución on-premise con retención certificada.

**Riesgo si no se hace:** Un equipo de 10+ devs operando un stack de observabilidad self-hosted sin un SRE dedicado acumula deuda operativa. El costo de un incidente causado por observabilidad degradada puede superar fácilmente el costo de una plataforma managed. La decisión de "construir vs. comprar" en observabilidad se vuelve económicamente distinta a escala.

---

## 7. Relación con ADRs previos

**ADR 0007 (Loki + Promtail):** El núcleo de la decisión de 0007 — Loki como backend de logs por bajo footprint — se mantiene válido y este ADR lo extiende. Lo que cambia: 0007 adoptaba Promtail como agente de recolección; este ADR lo reemplaza con OTel Collector como capa de instrumentación, lo que desacopla la recolección del backend. 0007 queda **parcialmente supersedido** en lo referente al agente; la elección de Loki como backend se hereda.

**ADR 0009 (EFK):** 0009 documentó la evaluación de EFK como alternativa. Este ADR descarta EFK explícitamente por costo de recursos (§2.2 y §5) y cierra la discusión sobre esa alternativa para el horizonte actual. 0009 queda **supersedido completamente** para nuestro escenario; si el contexto del §1 cambia según los umbrales descritos en §3, 0009 puede ser resucitado como punto de partida de una nueva evaluación.

**ADR de OTel (Parte 3):** El ADR de instrumentación con OTel de la Parte 3 estableció la adopción de OpenTelemetry como estándar de instrumentación de aplicaciones. Este ADR asume ese ADR como prerequisito y lo extiende al nivel de infraestructura (OTel Collector como componente de plataforma, no solo como SDK de aplicación). No hay conflicto; este ADR es un sucesor natural.

---

## 8. Referencias

- Mediciones empíricas: `docs/observability-final/measurements.md`
- Decision matrix: `docs/observability-final/decision-matrix.md`
- Reflexión vendor lock-in: `docs/observability-final/vendor-lockin-essay.md`
- CNCF Observability Whitepaper (2023): https://github.com/cncf/tag-observability/blob/main/whitepaper.md
- Charity Majors, Liz Fong-Jones, George Miranda — *Observability Engineering* (O'Reilly, 2022)
- Cindy Sridharan — *Distributed Systems Observability* (O'Reilly, 2018)
- Neal Ford, Mark Richards, Pramod Sadalage, Zhamak Dehghani — *Software Architecture: The Hard Parts*, cap. 7 "Documenting Architecture Decisions" (O'Reilly, 2021)
- OpenTelemetry Collector documentation: https://opentelemetry.io/docs/collector/
- Grafana Loki documentation: https://grafana.com/docs/loki/latest/