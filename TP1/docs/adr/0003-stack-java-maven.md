# ADR-0003: Stack Java 17 + Maven, no Python ni Node

- **Estado:** Aceptado
- **Fecha:** 2026-03-15
- **Autores:** Equipo TP1-SIP

## Contexto

La consigna admite cualquier stack (`pytest / JUnit / Jest`, `coverage.py / jest --coverage / jacoco`). El equipo cursó la materia anterior con Java y JUnit; ya tenía Maven instalado y conocimientos de Spotless/Checkstyle. La cátedra evalúa con `docker compose up`, así que el stack interno no condiciona la corrección — pero sí condiciona velocidad de desarrollo del equipo.

## Decisión

Adoptar **Java 17 (LTS) + Maven 3.x** como stack de desarrollo y build. Tooling de calidad: **JaCoCo** (cobertura), **JUnit 5 + Mockito** (tests), **Spotless con google-java-format** (formato), **Checkstyle** (lint).

## Consecuencias

- **Positivas:**
  - Reutilización de conocimiento: el equipo no perdió tiempo aprendiendo un stack nuevo durante la cursada.
  - Selenium 4 tiene su API más madura en Java; los ejemplos de la documentación oficial son Java-first.
  - Maven gestiona dependencias, build, lifecycle de tests y reportes en un solo `pom.xml`. No hace falta combinar `pip` + `pytest` + `coverage` + `tox`.
  - JaCoCo se integra en `mvn verify` con un solo plugin y emite reportes HTML+XML que el CI sube como artifact.
- **Negativas:**
  - Imagen Docker más pesada que un equivalente Python (JDK + Maven en builder, JRE en runtime ≈ 600MB).
  - Tiempo de cold-start de la JVM mayor que un script Python (~2s extra por ejecución del scraper).
  - Curva más empinada para colaboradores que vengan de Python/Node.
- **Neutrales:**
  - El ecosistema Selenium-Java requiere `WebDriverWait`/`ExpectedConditions` explícitos; no hay auto-await como Playwright.

## Alternativas consideradas

- **Python (Selenium + pytest + coverage.py):** stack más liviano y popular para scraping, pero el equipo tendría que reaprender `pytest` + fixtures + decoradores; no aporta nada en este TP donde la complejidad está en el DOM, no en el lenguaje.
- **Node + Playwright + Jest:** más rápido en runtime, pero ya descartado por ADR-0001 (Selenium fue elegido como framework).
