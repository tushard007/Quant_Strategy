package org.factor_investing.quant_strategy.strategies.risk_adjusted_momentum;

import org.factor_investing.quant_strategy.model.RiskAdjustedMomentumBacktestRun;
import org.factor_investing.quant_strategy.model.response.MomentumBacktestExecutionSummary;
import org.factor_investing.quant_strategy.model.response.RiskAdjustedMomentumBacktestResult;
import org.factor_investing.quant_strategy.repository.RiskAdjustedMomentumBacktestRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RiskAdjustedMomentumBacktestHistoryService {
    private final RiskAdjustedMomentumBacktestRunRepository repository;

    public RiskAdjustedMomentumBacktestHistoryService(RiskAdjustedMomentumBacktestRunRepository repository) { this.repository = repository; }

    @Transactional
    public UUID save(RiskAdjustedMomentumBacktestResult result, int entryRank, int retentionRank,
                     double transactionCostPercent, double slippagePercent,
                     double bufferAmount, double maximumLeverageAmount) {
        RiskAdjustedMomentumBacktestRun run = new RiskAdjustedMomentumBacktestRun();
        run.setStartDate(result.startDate()); run.setEndDate(result.endDate());
        run.setInitialCapital(result.initialCapital()); run.setEntryRank(entryRank); run.setRetentionRank(retentionRank);
        run.setBenchmark(result.benchmark()); run.setTransactionCostPercent(transactionCostPercent);
        run.setSlippagePercent(slippagePercent); run.setRiskFreeRatePercent(result.riskFreeRatePercent());
        run.setRebalanceMode(result.rebalanceMode()); run.setBufferAmount(bufferAmount);
        run.setMaximumLeverageAmount(maximumLeverageAmount);
        run.setBorrowingInterestRatePercent(result.borrowingInterestRatePercent());
        run.setStopModel(result.stopModel()); run.setTrailingStopPercent(result.trailingStopPercent());
        run.setCooldownWeeks(result.cooldownWeeks()); run.setBenchmarkSmaPeriod(result.benchmarkSmaPeriod());
        run.setBreadthThresholdPercent(result.breadthThresholdPercent());
        run.setWeakExposureCapPercent(result.weakExposureCapPercent());
        run.setFinalValue(result.finalValue()); run.setTotalReturn(result.totalReturn()); run.setCagr(result.cagr());
        run.setMaximumDrawdown(result.maximumDrawdown()); run.setSharpeRatio(result.sharpeRatio()); run.setResult(result);
        return repository.save(run).getId();
    }

    @Transactional(readOnly = true)
    public RiskAdjustedMomentumBacktestResult get(UUID id) {
        return repository.findById(id).map(RiskAdjustedMomentumBacktestRun::getResult)
                .orElseThrow(() -> new IllegalArgumentException("Risk-adjusted momentum backtest execution not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<MomentumBacktestExecutionSummary> history() {
        return repository.findAllProjectedByOrderByCreatedAtDesc().stream().map(row ->
                new MomentumBacktestExecutionSummary(row.getId(),row.getCreatedAt(),row.getStartDate(),row.getEndDate(),
                        row.getEntryRank(),row.getRetentionRank(),row.getBenchmark(),row.getRebalanceMode(),
                        row.getFinalValue(),row.getTotalReturn(),row.getCagr(),row.getMaximumDrawdown(),row.getSharpeRatio())).toList();
    }
}
