#!/bin/sh
# docker-entrypoint.sh — HIT8
# Permite pasar --browser chrome|firefox como argumento o via env BROWSER.

set -e

for arg in "$@"; do
    case "$arg" in
        --browser=*)
            BROWSER="${arg#*=}"
            ;;
    esac
done

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

exec java \
    -Dbrowser="$BROWSER" \
    -Dheadless="$HEADLESS" \
    -jar /app/scraper.jar
