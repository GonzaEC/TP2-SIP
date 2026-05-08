# OpenTelemetry y el vendor lock-in en observabilidad: un cálculo que cambió

## El problema histórico

Antes de OpenTelemetry, instrumentar una aplicación distribuida significaba elegir un SDK propietario: el agente de Datadog APM, el de New Relic, el de AppDynamics, el OneAgent de Dynatrace. Cada uno tenía sus propias APIs, su propio modelo de datos, sus propios formatos de exportación. El lock-in no era el backend de análisis —ese era reemplazable con esfuerzo— sino el SDK incrustado en el código de la aplicación. Cambiar de vendor implicaba re-instrumentar cientos de servicios y asumir un proyecto de 6 a 12 meses. La instrumentación era la deuda técnica que convertía a los vendors en inamovibles.

## Qué cambia con OTel

OpenTelemetry desacopla el plano de instrumentación del plano de almacenamiento y análisis. La aplicación habla contra una API estándar (las interfaces del SDK de OTel), emite telemetría en OTLP (OpenTelemetry Protocol, basado en protobuf/gRPC), y entrega esa telemetría al Collector. El Collector es un proxy stateless configurable en YAML que puede recibir OTLP y exportar simultáneamente a Datadog, Honeycomb, Grafana Cloud, Jaeger, o cualquier backend con un exporter disponible.

La consecuencia práctica: cambiar de backend pasa de ser un proyecto de re-instrumentación a ser un cambio de configuración del Collector. Un pipeline que hoy envía trazas a Datadog puede añadir un exporter a Tempo en paralelo, validar paridad, y eliminar el exporter original. El TCO de una migración baja un orden de magnitud porque el costo dominante ya no es tocar código de aplicación. El backend pasa de activo estratégico inamovible a commodity seleccionable por precio y funcionalidad.

## Por qué el estatus CNCF graduated importa

OpenTelemetry obtuvo el estatus de proyecto *graduated* en la CNCF en 2024. Ese sello no es cosmético: graduated implica gobernanza neutral multi-vendor, con un steering committee donde contribuyen simultáneamente Microsoft, Google, AWS, Splunk y Datadog (competidores directos en el mercado de observabilidad). Implica también un ciclo de release predecible y un compromiso formal de no-fork que protege la inversión de instrumentación.

El contraste con Logstash es instructivo. Logstash era gobernado por Elastic (single-vendor). En 2021, Elastic cambió la licencia de Elasticsearch y Logstash de Apache 2.0 a SSPL, partiendo la comunidad. Fluentd y Fluent Bit ganaron adopción acelerada precisamente por ser proyectos neutrales en la CNCF. La gobernanza multi-vendor no es un detalle de proceso: es la garantía de que ningún actor individual puede alterar unilateralmente las reglas del juego.

## Casos reales

**Shopify** construyó una plataforma de observabilidad interna llamada Observe, documentada por Elijah McPherson (director de ingeniería responsable del proyecto). La migración reemplazó agentes propietarios por OTel + Loki + Tempo + Grafana. Eliminar esos agentes propietarios redujo entre un 15% y un 20% el overhead de CPU en servicios de alto throughput. Fuente: [Shopify's Journey to Planet-Scale Observability](https://horovits.medium.com/shopifys-journey-to-planet-scale-observability-9c0b299a04dd) (febrero 2025).

**Cruise** (filial automotriz de General Motors) presentó en KubeCon NA 2023 la charla *"Today, Not Tomorrow: Scalable Strategies for Migrating to OpenTelemetry"* (Jason Anderson & Kevin Broadbridge), describiendo su migración hacia OTel para ganar opcionalidad sobre el backend y salir del lock-in de un vendor elegido años atrás. Programa: [opentelemetry.io/blog/2023/kubecon-na/](https://opentelemetry.io/blog/2023/kubecon-na/).

Grafana Labs, por su parte, ha declarado que OTel es el camino de ingestión canónico para Loki 3.x, lo que cierra el círculo: incluso los vendors de backend están convergiendo en OTLP como protocolo de entrada estándar.

## Cierre honesto

OpenTelemetry no elimina el vendor lock-in. Lo redistribuye. El nuevo lock-in tiene dos dimensiones:

Primero, el lock-in a OTel mismo. Si el proyecto se fragmentara —si surgiera un fork con suficiente tracción industrial, o si el steering committee perdiera el equilibrio de poder que hoy lo hace funcionar— todas las organizaciones que instrumentaron contra la API de OTel sufrirían las consecuencias. El historial de Logstash es un recordatorio de que eso puede ocurrir.

Segundo, el lock-in al backend de almacenamiento. Los datos siguen viviendo en Loki, Elasticsearch, Datadog o Grafana Cloud. Migrar el histórico de datos entre backends sigue siendo costoso, y las queries, dashboards y alertas están escritas en lenguajes específicos de cada plataforma (LogQL, KQL, NRQL, etc.).

Lo que OTel sí cambia es el costo de ese lock-in. Cambiar el SDK era un proyecto de reescritura masiva. Cambiar el backend de almacenamiento es un proyecto de migración de datos y re-escritura de queries, más barato y más acotado. Y el lock-in al SDK —que era el más difícil de romper— está ahora distribuido entre una coalición de vendors con incentivos divergentes, gobernada por una fundación neutral. Eso es progreso real, aunque no sea el fin del problema.