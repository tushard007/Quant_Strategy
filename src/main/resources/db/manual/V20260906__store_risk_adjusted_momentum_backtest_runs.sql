CREATE TABLE IF NOT EXISTS risk_adjusted_momentum_backtest_run (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    initial_capital DOUBLE PRECISION NOT NULL,
    entry_rank INTEGER NOT NULL,
    retention_rank INTEGER NOT NULL,
    benchmark VARCHAR(255) NOT NULL,
    transaction_cost_percent DOUBLE PRECISION NOT NULL,
    slippage_percent DOUBLE PRECISION NOT NULL,
    risk_free_rate_percent DOUBLE PRECISION NOT NULL,
    rebalance_mode VARCHAR(255) NOT NULL,
    buffer_amount DOUBLE PRECISION NOT NULL,
    maximum_leverage_amount DOUBLE PRECISION NOT NULL,
    borrowing_interest_rate_percent DOUBLE PRECISION NOT NULL,
    stop_model VARCHAR(255) NOT NULL,
    trailing_stop_percent DOUBLE PRECISION NOT NULL,
    cooldown_weeks INTEGER NOT NULL,
    benchmark_sma_period INTEGER NOT NULL,
    breadth_threshold_percent DOUBLE PRECISION NOT NULL,
    weak_exposure_cap_percent DOUBLE PRECISION NOT NULL,
    final_value DOUBLE PRECISION NOT NULL,
    total_return DOUBLE PRECISION NOT NULL,
    cagr DOUBLE PRECISION NOT NULL,
    maximum_drawdown DOUBLE PRECISION NOT NULL,
    sharpe_ratio DOUBLE PRECISION NOT NULL,
    result JSONB NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_risk_adjusted_momentum_backtest_run_created_at
    ON risk_adjusted_momentum_backtest_run (created_at DESC);
