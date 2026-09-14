CREATE TABLE IF NOT EXISTS breadth_daily_snapshot (
    id UUID PRIMARY KEY,
    universe VARCHAR(50) NOT NULL,
    trading_date DATE NOT NULL,
    methodology VARCHAR(50) NOT NULL,
    score_configuration_version INTEGER NOT NULL,
    coverage_percent DOUBLE PRECISION NOT NULL,
    quality_status VARCHAR(20) NOT NULL,
    final_score DOUBLE PRECISION NOT NULL,
    regime VARCHAR(20) NOT NULL,
    indicator_values JSONB NOT NULL,
    component_scores JSONB NOT NULL,
    component_reasons JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_breadth_daily_snapshot UNIQUE (universe, trading_date),
    CONSTRAINT ck_breadth_daily_snapshot_coverage CHECK (coverage_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_breadth_daily_snapshot_score CHECK (final_score BETWEEN 0 AND 100)
);

CREATE INDEX IF NOT EXISTS idx_breadth_daily_snapshot_universe_date
    ON breadth_daily_snapshot (universe, trading_date DESC);
