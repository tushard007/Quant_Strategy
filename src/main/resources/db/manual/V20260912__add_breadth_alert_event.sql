CREATE TABLE IF NOT EXISTS breadth_alert_event (
    id UUID PRIMARY KEY,
    alert_type VARCHAR(100) NOT NULL,
    universe VARCHAR(50) NOT NULL,
    trigger_date DATE NOT NULL,
    trigger_values JSONB NOT NULL,
    message VARCHAR(500) NOT NULL,
    delivery_state VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    dedup_key VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_breadth_alert_event_dedup_key UNIQUE (dedup_key),
    CONSTRAINT ck_breadth_alert_event_attempts CHECK (attempts >= 0)
);

CREATE INDEX IF NOT EXISTS idx_breadth_alert_event_delivery_state
    ON breadth_alert_event (delivery_state, created_at);
