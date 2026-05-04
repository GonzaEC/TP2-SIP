# ADR-0005: Orquestación con Kubernetes (Job/CronJob)

- **Estado:** Aceptado
- **Fecha:** 2026-05-02
- **Autores:** Equipo TP1-SIP

## Contexto

El HIT 7 exige demostrar que el scraper se puede desplegar en un orquestador, no solo correr en Docker local. La consigna menciona específicamente k3s como cluster objetivo. Necesitábamos elegir entre varias formas de ejecutar cargas de trabajo periódicas en Kubernetes:

- **Job one-off:** ejecución puntual para probar el scraper una vez.
- **CronJob:** ejecución programada (cada hora) para scraping recurrente.
- **Deployment + sidecar:** mantiene un pod corriendo permanentemente.
- **Quedarnos solo con docker-compose + cron del host:** no cumple la consigna.

Además, la consigna exige externalizar configuración (ConfigMap) y persistir outputs (PVC), lo cual condiciona la arquitectura.

## Decisión

Usar **Kubernetes Job para ejecuciones puntuales** y **CronJob para ejecuciones programadas**, con **ConfigMap** para configuración y **PVC** para persistencia de outputs.

## Consecuencias

- **Positivas:**
  - Cumplimiento directo de la consigna: orquestación nativa de Kubernetes, no Docker ad-hoc.
  - El CronJob maneja automáticamente el scheduling (`0 * * * *`) sin depender del cron del host ni de servicios externos.
  - El `backoffLimit: 2` de los Jobs gestiona reintentos automáticos ante fallos temporales.
  - ConfigMap permite cambiar productos/browser sin reconstruir la imagen Docker.
  - PVC con `local-path` (incluido en k3s) proporciona persistencia real sin depender de almacenamiento en red.
  - Jobs completados no dejan pods corriendo indefinidamente, ahorrando recursos del cluster.

- **Negativas:**
  - Mayor complejidad operativa vs Docker Compose: requiere cluster k3s, kubectl, y entender conceptos de Kubernetes (pods, jobs, cronjobs, pvcs).
  - El PVC `ReadWriteOnce` solo permite un pod escribiendo a la vez; no escala horizontalmente (no es necesario para este caso de uso).
  - Si k3s se reinicia, los outputs antiguos pueden quedar huérfanos dependiendo de la política de retención.

- **Neutrales:**
  - La imagen Docker (`ml-scraper:latest`) es la misma que usa el HIT 6, solo cambia el entorno de ejecución.
  - Los manifiestos YAML son declarativos y versionables, lo que facilita reproducir el despliegue en cualquier cluster k3s.

## Alternativas consideradas

- **Deployment con sidecar:** descartado — un scraper es una tarea batch finita, no un servicio long-running. Mantener un Deployment permanente consumiría recursos innecesariamente.
- **docker-compose + cron del host:** descartado — no cumple el requisito de orquestador Kubernetes de la consigna. Además, acopla el scheduling al OS del host.
- **Argo Workflows / Tekton:** descartado — agregan una capa de orquestación muy potente pero excesiva para un único scraper con 3 productos. Requieren instalar controladores adicionales en el cluster.
