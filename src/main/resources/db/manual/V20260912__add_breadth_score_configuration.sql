CREATE TABLE IF NOT EXISTS breadth_score_configuration (
    id UUID PRIMARY KEY,
    version INTEGER NOT NULL,
    weights JSONB NOT NULL,
    green_threshold DOUBLE PRECISION NOT NULL,
    amber_threshold DOUBLE PRECISION NOT NULL,
    vix_spike_percent DOUBLE PRECISION NOT NULL,
    rising_falling_lookback_sessions INTEGER NOT NULL,
    expansion_contraction_lookback_sessions INTEGER NOT NULL,
    effective_from DATE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_breadth_score_configuration_version UNIQUE (version),
    CONSTRAINT ck_breadth_score_configuration_thresholds CHECK (
        amber_threshold BETWEEN 0 AND 100
        AND green_threshold BETWEEN 0 AND 100
        AND amber_threshold < green_threshold
    ),
    CONSTRAINT ck_breadth_score_configuration_values CHECK (
        version > 0
        AND vix_spike_percent >= 0
        AND rising_falling_lookback_sessions > 0
        AND expansion_contraction_lookback_sessions > 0
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_breadth_score_configuration_active
    ON breadth_score_configuration (active)
    WHERE active = true;

INSERT INTO breadth_score_configuration (
    id, version, weights, green_threshold, amber_threshold, vix_spike_percent,
    rising_falling_lookback_sessions, expansion_contraction_lookback_sessions,
    effective_from, active, created_at
)
SELECT
    gen_random_uuid(),
    1,
    '{
        "AD_LINE_TREND": 10,
        "BREADTH_50D": 15,
        "BREADTH_200D": 15,
        "MCCLELLAN_OSCILLATOR": 10,
        "MCCLELLAN_SUMMATION_INDEX": 10,
        "NET_NEW_HIGHS_LOWS": 10,
        "ZWEIG_BREADTH_THRUST": 5,
        "VOLUME_BREADTH_TRIN": 5,
        "BULLISH_PERCENT_PROXY": 10,
        "SECTOR_PARTICIPATION": 5,
        "PRICE_BREADTH_CONFIRMATION": 5
    }'::jsonb,
    70, 40, 10,
    5, 10,
    CURRENT_DATE, true, now()
WHERE NOT EXISTS (SELECT 1 FROM breadth_score_configuration WHERE version = 1);
