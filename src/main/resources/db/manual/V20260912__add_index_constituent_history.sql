CREATE TABLE IF NOT EXISTS index_constituent_history (
    id UUID PRIMARY KEY,
    index_name VARCHAR(50) NOT NULL,
    stock_symbol VARCHAR(50) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    source VARCHAR(255) NOT NULL,
    imported_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_index_constituent_history UNIQUE (index_name, stock_symbol, effective_from),
    CONSTRAINT ck_index_constituent_history_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX IF NOT EXISTS idx_index_constituent_history_lookup
    ON index_constituent_history (index_name, stock_symbol);
