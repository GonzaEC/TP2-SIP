-- V1__create_scrape_results.sql
-- Esquema inicial para almacenar resultados del scraper de MercadoLibre.

CREATE TABLE IF NOT EXISTS scrape_results (
    id                  SERIAL          PRIMARY KEY,
    producto            VARCHAR(255)    NOT NULL,
    titulo              TEXT            NOT NULL,
    precio              BIGINT,
    link                TEXT,
    tienda_oficial      VARCHAR(255),
    envio_gratis        BOOLEAN         NOT NULL DEFAULT FALSE,
    cuotas_sin_interes  VARCHAR(255),
    scraped_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_scrape_results_producto ON scrape_results (producto);
CREATE INDEX IF NOT EXISTS idx_scrape_results_scraped_at ON scrape_results (scraped_at DESC);

CREATE TABLE IF NOT EXISTS price_stats (
    id                  SERIAL              PRIMARY KEY,
    producto            VARCHAR(255)        NOT NULL,
    precio_min          BIGINT,
    precio_max          BIGINT,
    precio_mediana      DOUBLE PRECISION,
    precio_desviacion   DOUBLE PRECISION,
    total_con_precio    INT                 NOT NULL DEFAULT 0,
    scraped_at          TIMESTAMPTZ         NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_price_stats_producto ON price_stats (producto);
