# ADR-0001: Selenium WebDriver como framework de automatización

- **Estado:** Aceptado
- **Fecha:** 2026-03-15
- **Autores:** Equipo TP1-SIP

## Contexto

El TP exige automatizar un scraper sobre MercadoLibre Argentina que abra un browser real, ejecute búsquedas, aplique filtros vía DOM y extraiga datos. La cátedra menciona explícitamente **Selenium** como referencia de la cursada, y el contenido de teoría se basa en sus primitivas (`WebDriverWait`, `ExpectedConditions`, `By`). El equipo tiene experiencia previa con Java y JUnit, no con stacks Node/Python. La consigna también pide soporte multi-browser real (Chrome **y** Firefox).

## Decisión

Usar **Selenium WebDriver 4.20.0** con bindings de **Java 17**.

## Consecuencias

- **Positivas:**
  - Alineación 1:1 con la teoría del curso → menor curva de aprendizaje y consultas con la cátedra más directas.
  - Soporte oficial de drivers para Chrome (chromedriver) y Firefox (geckodriver), ambos pinneados en el `Dockerfile`.
  - Ecosistema maduro: `WebDriverWait` + `ExpectedConditions` cubren el requisito de explicit waits sin `Thread.sleep()`.
- **Negativas:**
  - Más verboso que Playwright/Puppeteer, especialmente para navegación dinámica con JavaScript.
  - El protocolo W3C WebDriver agrega latencia comparado con Chrome DevTools Protocol que usa Playwright.
- **Neutrales:**
  - Atado al ecosistema Java/Maven (ver ADR-0003).

## Alternativas consideradas

- **Playwright (Node/Python):** más rápido y con auto-waiting, pero exige stack Node o Python (el equipo no tiene experiencia) y se aleja de la teoría del curso.
- **Puppeteer:** Chrome-only, lo cual incumple el requisito de multi-browser.
- **Cypress:** orientado a tests E2E de SPAs propias, no a scraping de sitios externos. Restringido a un tab/origen, no encaja en este caso de uso.
