#!/bin/sh
# docker-entrypoint.sh
# Permite pasar --browser chrome|firefox como argumento de docker run,
# o bien controlarlo mediante la variable de entorno BROWSER.
#
# Uso:
#   docker run --rm -v $(pwd)/output:/app/output ml-scraper:latest
#   docker run --rm -v $(pwd)/output:/app/output ml-scraper:latest --browser firefox
#   BROWSER=firefox docker run --rm -v $(pwd)/output:/app/output ml-scraper:latest

set -e

# Parsear --browser desde los argumentos si se pasa
for arg in "$@"; do
    case "$arg" in
        --browser=*)
            BROWSER="${arg#*=}"
            ;;
    esac
done

# Manejar "--browser firefox" (dos tokens separados)
PREV=""
for arg in "$@"; do
    if [ "$PREV" = "--browser" ]; then
        BROWSER="$arg"
    fi
    PREV="$arg"
done

export BROWSER="${BROWSER:-chrome}"
export HEADLESS="${HEADLESS:-true}"

echo "[entrypoint] Browser: $BROWSER | Headless: $HEADLESS"

# Ejecutar el scraper
exec java \
    -Dbrowser="$BROWSER" \
    -Dheadless="$HEADLESS" \
    -jar /app/scraper.jar
