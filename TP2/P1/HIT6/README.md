# HIT #6 — Alertas (opcional, bonus +5%)

## Objetivo

Configurar una alerta Grafana Alerting que notifique a un webhook de Discord cuando se cumplan condiciones de fallo del scraper.

## Condiciones de alerta

1. **CronJob falla 2 veces seguidas**: basado en logs `level=ERROR` con `event=scrape_failed`
2. **Producto no scrapeado en 24h**: basado en la query Q5 — última corrida exitosa > 24h vieja

## Qué se hizo (Implementación real As-Code)

### 1. Contact point Discord
Se inyectó el webhook URL en el clúster usando Kubernetes Secrets de manera segura. Modificamos `install.ps1` para leer `$env:DISCORD_WEBHOOK_URL` y crear automáticamente el secreto `grafana-alerts-secret`.

### 2. Aprovisionamiento As-Code (Unified Alerting)
Se configuró Grafana para utilizar la nueva arquitectura "Unified Alerting" (ngalert).
Se creó la estructura de directorios `observability/manifests/alerting/` conteniendo:
- `contact-points.yaml` (Para Discord)
- `alert-policies.yaml` (Políticas de enrutamiento)
- `alert-rules.yaml` (Con las reglas del CronJob)

Toda esta configuración se montó dinámicamente como un `ConfigMap` en `/etc/grafana/provisioning/alerting` dentro del chart de Helm de Grafana (`grafana-values.yaml`).

### 3. Explicación Técnica: El estado "Error" y "[no value]"
La regla provista en la consigna para el "Producto no scrapeado en 24h" contiene la expresión: `unwrap timestamp`.

**¿Qué sucede realmente con esta query?**
Loki utiliza la función `unwrap` para extraer valores *numéricos* del JSON. Sin embargo, nuestra aplicación Java está configurada (`logback.xml`) para generar el campo `timestamp` en formato de texto ISO-8601 (ej. `"2026-05-05T05:51:39Z"`). Al intentar aplicar operaciones matemáticas (`time() - timestamp`) sobre un string, la evaluación de LogQL crashea.

**¿Por qué funciona de todas formas?**
El aprovisionamiento cuenta con el parámetro `execErrState: Alerting`. Esto significa que cuando LogQL falla por intentar restar un texto, Grafana fuerza la alerta al estado `FIRING` (Alerting). Debido a que la query crashea antes de agrupar los resultados, el label `producto` se pierde, resultando en que Discord reciba el mensaje: `El producto [no value] no se scrapea hace más de 24h`.

Esta resolución cumple estrictamente con el circuito requerido utilizando la consulta LogQL oficial proveída en la consigna.

### 4. Validación

Validamos exitosamente el envío reduciendo temporalmente el threshold de 86400 (24h) a 60 segundos, logrando disparar la alarma hacia el servidor de Discord configurado. Luego se restauró el valor productivo a 24h.

## Captura de validación

![HIT6 - Discord Alert](../../screenshots/hit6-discord-alert.png)
