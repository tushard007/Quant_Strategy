ALTER TABLE breadth_daily_snapshot
    ADD COLUMN IF NOT EXISTS component_reasons JSONB NOT NULL DEFAULT '{}'::jsonb;
