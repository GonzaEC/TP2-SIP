# ADR 0006 — Por qué Chrome evade el anti-bot de MercadoLibre y Firefox no

**Estado:** Aceptado  
**Fecha:** 2026-05-02

---

## Contexto

En el job `docker-scraper` del CI, el scraper corriendo en Chrome genera los 3 JSONs con éxito, mientras que Firefox es redirigido a `account-verification` y falla en todos los productos. Ambos browsers usan la misma imagen Docker, la misma IP de datacenter de GitHub Actions y los mismos products de entrada.

---

## Causa raíz

La diferencia está en las flags de anti-detección configuradas en `BrowserFactory.java`.

### Chrome — más camuflado

```java
opts.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
opts.addArguments("--disable-blink-features=AutomationControlled");
// headless moderno (menos detectable que --headless clásico)
opts.addArguments("--headless=new", ...);
```

- `--disable-blink-features=AutomationControlled` **elimina** la propiedad `navigator.webdriver = true` del contexto JavaScript del navegador. Esa propiedad es el check más básico que usan los sistemas anti-bot para detectar Selenium.
- `excludeSwitches: enable-automation` elimina el banner visual de "Chrome está siendo controlado por software automatizado", que también puede ser detectado por fingerprinting.
- `--headless=new` usa el modo headless de Chrome 112+, que comparte más código con el modo gráfico y genera un fingerprint de JS/CSS mucho más similar al de un browser real.

### Firefox — más expuesto

```java
opts.addPreference("dom.webdriver.enabled", false);
opts.addPreference("useAutomationExtension", false);
// headless clásico
opts.addArguments("--headless", ...);
```

- `dom.webdriver.enabled = false` hace lo mismo en teoría, pero en Geckodriver la propiedad `navigator.webdriver` **no siempre queda oculta** correctamente según la versión, y sitios como MercadoLibre lo detectan.
- El headless clásico de Firefox (`--headless`) tiene diferencias medibles en el fingerprint de canvas, WebGL y timing de eventos comparado con Firefox en modo gráfico.
- Geckodriver deja trazas distintas en headers HTTP (Accept-Language por defecto, orden de headers) que pueden ser detectadas por análisis de tráfico.

---

## Decisión

Se mantiene la asimetría actual. Chrome es suficientemente camuflado para los propósitos del TP. Firefox se documenta como "best-effort" y el job `docker-scraper` tiene `continue-on-error: true` para no romper el CI cuando MercadoLibre bloquea.

No se invierte más tiempo en mejorar el camuflaje de Firefox en CI porque:
1. El bloqueo es por IP de datacenter, no solo por fingerprint — cualquier mejora sería parcial.
2. El objetivo del TP es demostrar soporte multi-browser, no evasión de anti-bot.
3. Los tests unitarios (que sí corren ambos browsers en CI) son la evidencia real del soporte Firefox.

---

## Consecuencias

- Los artefactos `output-json-docker-firefox` y `screenshots-docker-firefox` pueden estar vacíos en cada run de CI — es esperado.
- El `::notice::` al final del job reporta el estado real (`success`/`failed`) sin fallar el pipeline.
- Si en el futuro MercadoLibre también bloquea Chrome desde datacenter, el mismo `continue-on-error: true` lo cubre.
