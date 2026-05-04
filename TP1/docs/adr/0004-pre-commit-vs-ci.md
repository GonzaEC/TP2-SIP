# ADR-0004: División de responsabilidades entre pre-commit local y CI remoto

- **Estado:** Aceptado
- **Fecha:** 2026-04-10
- **Autores:** Equipo TP1-SIP

## Contexto

La consigna pide tanto **pre-commit hooks locales** (gitleaks, linter, formatter) como **CI remoto** (build Docker, tests + cobertura ≥70%, scraper headless en matriz Chrome/Firefox, Gitleaks). Hay solapamiento natural — Gitleaks corre en ambos lados, Checkstyle también. Si replicamos todo en ambos lados pagamos doble: feedback más lento al desarrollador y minutos de CI desperdiciados. Si tercerizamos todo a CI, los problemas triviales (formato roto, secret pegado por accidente) llegan al PR y bloquean al equipo.

## Decisión

Usar la regla **"barato y local en pre-commit; caro o no-determinista en CI"**:

| Hook                          | Pre-commit (local) | CI remoto |
|-------------------------------|:------------------:|:---------:|
| Gitleaks (secrets)            | ✅                 | ✅ (defensa en profundidad) |
| Spotless (formatter)          | ✅ (auto-fix)      | ❌        |
| Checkstyle (linter)           | ✅                 | ✅ (vía `mvn verify`) |
| `end-of-file-fixer`, EOL=lf   | ✅                 | ❌        |
| Tests unitarios + JaCoCo 70%  | ❌                 | ✅        |
| Build Docker multi-stage      | ❌                 | ✅        |
| Scraper headless matrix       | ❌                 | ✅        |
| E2E contra MercadoLibre real  | ❌                 | ✅ (solo `main`) |

## Consecuencias

- **Positivas:**
  - Feedback inmediato (<5s) para problemas de formato y secrets, antes de que el commit se cree.
  - El CI no se cae por trivialidades de formato; cuando falla, casi siempre es un problema real.
  - Gitleaks duplicado actúa como defensa en profundidad: aunque alguien haga `git commit --no-verify`, el push sigue siendo bloqueado.
- **Negativas:**
  - Onboarding requiere `pip install pre-commit && pre-commit install` (documentado en el README).
  - Los hooks de Spotless/Checkstyle invocan Maven, que es lento en frío (~15s primer commit). Mitigado por la caché local de Maven (`~/.m2`).
  - Quien use `--no-verify` se salta los locales; depende del CI como red de seguridad.
- **Neutrales:**
  - El `.pre-commit-config.yaml` queda como contrato versionado del equipo, no como configuración personal del IDE.

## Alternativas consideradas

- **Solo CI:** descartado — los desarrolladores reciben feedback recién al pushear, lo que rompe el flow y consume runners.
- **Solo pre-commit:** descartado — alguien con `--no-verify` puede romper `main` sin darse cuenta. Además, la cobertura JaCoCo no tiene sentido a nivel commit (se mide sobre el suite completo).
- **Husky (Node):** más moderno pero requiere Node como dependencia del repo. El framework `pre-commit` (Python) es agnóstico al stack y ya es estándar de facto.
