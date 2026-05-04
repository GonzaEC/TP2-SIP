# ─────────────────────────────────────────────────────────────────────────────
# STAGE 1 — builder
# ─────────────────────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
COPY checkstyle.xml ./checkstyle.xml
RUN mvn package -DskipTests -q

# ─────────────────────────────────────────────────────────────────────────────
# STAGE 2 — runtime
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17.0.11_9-jre-jammy AS runtime

ENV BROWSER=chrome \
    HEADLESS=true \
    DISPLAY=:99

WORKDIR /app

RUN apt-get update -qq && \
    apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        gnupg \
        wget \
        xvfb \
    && rm -rf /var/lib/apt/lists/*

RUN wget -q -O /usr/share/keyrings/google-chrome.asc \
        "https://dl.google.com/linux/linux_signing_key.pub" && \
    echo "deb [arch=amd64 signed-by=/usr/share/keyrings/google-chrome.asc] http://dl.google.com/linux/chrome/deb/ stable main" \
        > /etc/apt/sources.list.d/google-chrome.list && \
    apt-get update -qq && \
    apt-get install -y --no-install-recommends google-chrome-stable && \
    rm -rf /var/lib/apt/lists/*

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

COPY --from=builder /build/target/hit8-scraper-1.0-SNAPSHOT.jar /app/scraper.jar

RUN mkdir -p /app/output /app/screenshots

COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh

VOLUME ["/app/output", "/app/screenshots"]

ENTRYPOINT ["/app/docker-entrypoint.sh"]
