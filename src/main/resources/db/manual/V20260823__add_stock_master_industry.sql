ALTER TABLE t_nse_stock_master_data
    ADD COLUMN IF NOT EXISTS industry VARCHAR(150);
