# ─────────────────────────────────────────────────────────────────────────────
# STAGE 1 — builder
#   Compila el proyecto y empaqueta el fat-jar con todas las dependencias.
#   Usa Maven oficial con JDK 17 (pin de versión, sin :latest).
# ─────────────────────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /build

# Cachear dependencias antes de copiar el código fuente.
# Si pom.xml no cambia, Docker reutiliza esta capa.
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copiar fuentes + reglas de Checkstyle (el plugin las necesita en fase validate)
COPY src ./src
COPY checkstyle.xml ./checkstyle.xml
RUN mvn package -DskipTests -q

# ─────────────────────────────────────────────────────────────────────────────
# STAGE 2 — runtime
#   Imagen mínima con JRE 17 + Chrome + Firefox + sus drivers.
#   Pin: eclipse-temurin:17.0.11_9-jre-jammy  (Ubuntu 22.04 LTS)
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17.0.11_9-jre-jammy AS runtime

# ── Variables de configuración por defecto ───────────────────────────────────
ENV BROWSER=chrome \
    HEADLESS=true \
    DISPLAY=:99

WORKDIR /app

# ── Utilidades base + Xvfb (por si se necesita modo no-headless en el futuro) ─
RUN apt-get update -qq && \
    apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        gnupg \
        wget \
        xvfb \
    && rm -rf /var/lib/apt/lists/*

# ── Instalar Google Chrome stable desde el repositorio APT oficial ───────────
# Usar el repo APT en vez del .deb directo para que apt resuelva todas las
# dependencias automáticamente (libgbm1, libgtk-3-0, libvulkan1, etc.) y para
# evitar 404s del pool/main cuando Google saca versiones viejas.
# El driver (chromedriver) lo resuelve Selenium Manager en runtime contra la
# versión exacta de Chrome instalada, así nos evitamos el version-matching.
RUN wget -q -O /usr/share/keyrings/google-chrome.asc \
        "https://dl.google.com/linux/linux_signing_key.pub" && \
    echo "deb [arch=amd64 signed-by=/usr/share/keyrings/google-chrome.asc] http://dl.google.com/linux/chrome/deb/ stable main" \
        > /etc/apt/sources.list.d/google-chrome.list && \
    apt-get update -qq && \
    apt-get install -y --no-install-recommends google-chrome-stable && \
    rm -rf /var/lib/apt/lists/*

# ── Instalar Firefox desde el repositorio APT oficial de Mozilla ─────────────
# Ubuntu 22.04 movió Firefox a snap (no funciona en containers) y sacó
# `firefox-esr` del repo principal. Mozilla mantiene su propio repo APT en
# packages.mozilla.org, que es la fuente recomendada para containers.
# geckodriver lo resuelve Selenium Manager en runtime.
RUN install -d -m 0755 /etc/apt/keyrings && \
    wget -q -O- https://packages.mozilla.org/apt/repo-signing-key.gpg \
        | tee /etc/apt/keyrings/packages.mozilla.org.asc > /dev/null && \
    echo "deb [signed-by=/etc/apt/keyrings/packages.mozilla.org.asc] https://packages.mozilla.org/apt mozilla main" \
        > /etc/apt/sources.list.d/mozilla.list && \
    printf 'Package: *\nPin: origin packages.mozilla.org\nPin-Priority: 1000\n' \
        > /etc/apt/preferences.d/mozilla && \
    apt-get update -qq && \
    apt-get install -y --no-install-recommends firefox && \
    rm -rf /var/lib/apt/lists/*

# ── Copiar el jar generado en el stage builder ───────────────────────────────
COPY --from=builder /build/target/hit6-scraper-1.0-SNAPSHOT.jar /app/scraper.jar

# ── Directorios de salida (montados desde el host) ───────────────────────────
RUN mkdir -p /app/output /app/screenshots

# ── Script de entrada ────────────────────────────────────────────────────────
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh

VOLUME ["/app/output", "/app/screenshots"]

ENTRYPOINT ["/app/docker-entrypoint.sh"]
