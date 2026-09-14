-- Run once before deploying the timeframe-separated price entities in environments
-- where Hibernate ddl-auto is disabled.

UPDATE t_stock_price_data_json SET time_frame = 'DAILY' WHERE time_frame IS NULL;
UPDATE t_etf_price_data_json SET time_frame = 'DAILY' WHERE time_frame IS NULL;
UPDATE t_index_price_data_json SET time_frame = 'DAILY' WHERE time_frame IS NULL;

-- Remove the old one-to-one unique constraints on the master-symbol foreign keys.
DO $$
DECLARE constraint_row record;
BEGIN
    FOR constraint_row IN
        SELECT conrelid::regclass AS table_name, conname
        FROM pg_constraint
        WHERE contype = 'u'
          AND conrelid IN ('t_stock_price_data_json'::regclass, 't_etf_price_data_json'::regclass, 't_index_price_data_json'::regclass)
          AND array_length(conkey, 1) = 1
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT IF EXISTS %I', constraint_row.table_name, constraint_row.conname);
    END LOOP;
END $$;

-- Protect against legacy duplicate entity rows before adding composite uniqueness.
DELETE FROM t_stock_price_data_json older USING t_stock_price_data_json newer
WHERE older.stock_symbol = newer.stock_symbol AND older.time_frame = newer.time_frame AND older.id < newer.id;
DELETE FROM t_etf_price_data_json older USING t_etf_price_data_json newer
WHERE older.etf_symbol = newer.etf_symbol AND older.time_frame = newer.time_frame AND older.id < newer.id;
DELETE FROM t_index_price_data_json older USING t_index_price_data_json newer
WHERE older.index_symbol = newer.index_symbol AND older.time_frame = newer.time_frame AND older.id < newer.id;

ALTER TABLE t_stock_price_data_json ALTER COLUMN time_frame SET NOT NULL;
ALTER TABLE t_etf_price_data_json ALTER COLUMN time_frame SET NOT NULL;
ALTER TABLE t_index_price_data_json ALTER COLUMN time_frame SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_stock_price_symbol_timeframe ON t_stock_price_data_json(stock_symbol, time_frame);
CREATE UNIQUE INDEX IF NOT EXISTS uk_etf_price_symbol_timeframe ON t_etf_price_data_json(etf_symbol, time_frame);
CREATE UNIQUE INDEX IF NOT EXISTS uk_index_price_symbol_timeframe ON t_index_price_data_json(index_symbol, time_frame);
