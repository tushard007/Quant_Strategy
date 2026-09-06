package org.factor_investing.quant_strategy.strategies.momentum;

import org.factor_investing.quant_strategy.model.MomentumBacktestRun;
import org.factor_investing.quant_strategy.model.response.MomentumBacktestExecutionSummary;
import org.factor_investing.quant_strategy.model.response.MomentumBacktestResult;
import org.factor_investing.quant_strategy.repository.MomentumBacktestRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class MomentumBacktestHistoryService {
    private final MomentumBacktestRunRepository repository;

    public MomentumBacktestHistoryService(MomentumBacktestRunRepository repository) { this.repository = repository; }

    @Transactional
    public UUID save(MomentumBacktestResult result, int entryRank, int retentionRank,
                     double transactionCostPercent, double slippagePercent,
                     double bufferAmount, double maximumLeverageAmount) {
        MomentumBacktestRun run = new MomentumBacktestRun();
        run.setStartDate(result.startDate()); run.setEndDate(result.endDate());
        run.setInitialCapital(result.initialCapital()); run.setEntryRank(entryRank); run.setRetentionRank(retentionRank);
        run.setBenchmark(result.benchmark()); run.setTransactionCostPercent(transactionCostPercent);
        run.setSlippagePercent(slippagePercent); run.setRiskFreeRatePercent(result.riskFreeRatePercent());
        run.setRebalanceMode(result.rebalanceMode()); run.setBufferAmount(bufferAmount);
        run.setMaximumLeverageAmount(maximumLeverageAmount);
        run.setBorrowingInterestRatePercent(result.borrowingInterestRatePercent());
        run.setFinalValue(result.finalValue()); run.setTotalReturn(result.totalReturn()); run.setCagr(result.cagr());
        run.setMaximumDrawdown(result.maximumDrawdown()); run.setSharpeRatio(result.sharpeRatio()); run.setResult(result);
        return repository.save(run).getId();
    }

    @Transactional(readOnly = true)
    public MomentumBacktestResult get(UUID id) {
        return repository.findById(id).map(MomentumBacktestRun::getResult)
                .orElseThrow(() -> new IllegalArgumentException("Momentum backtest execution not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<MomentumBacktestExecutionSummary> history() {
        return repository.findAllProjectedByOrderByCreatedAtDesc().stream().map(row ->
                new MomentumBacktestExecutionSummary(row.getId(),row.getCreatedAt(),row.getStartDate(),row.getEndDate(),
                        row.getEntryRank(),row.getRetentionRank(),row.getBenchmark(),row.getRebalanceMode(),
                        row.getFinalValue(),row.getTotalReturn(),row.getCagr(),row.getMaximumDrawdown(),row.getSharpeRatio())).toList();
    }
}
