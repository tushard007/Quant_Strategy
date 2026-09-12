package org.factor_investing.quant_strategy.strategies.breadth_backtest;

import org.factor_investing.quant_strategy.model.BreadthBacktestRun;
import org.factor_investing.quant_strategy.model.BreadthBacktestRunType;
import org.factor_investing.quant_strategy.model.BreadthBacktestStatus;
import org.factor_investing.quant_strategy.model.request.BreadthFilteredMomentumBacktestRequest;
import org.factor_investing.quant_strategy.model.request.BreadthThresholdSensitivityRequest;
import org.factor_investing.quant_strategy.model.response.BreadthBacktestRunDetail;
import org.factor_investing.quant_strategy.model.response.BreadthBacktestRunResult;
import org.factor_investing.quant_strategy.model.response.BreadthBacktestRunSummary;
import org.factor_investing.quant_strategy.model.response.BreadthFilteredMomentumBacktestResult;
import org.factor_investing.quant_strategy.model.response.BreadthThresholdSensitivityResult;
import org.factor_investing.quant_strategy.model.response.RiskAdjustedMomentumBacktestResult;
import org.factor_investing.quant_strategy.repository.BreadthBacktestRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class BreadthBacktestHistoryService {
    private final BreadthBacktestRunRepository repository;

    public BreadthBacktestHistoryService(BreadthBacktestRunRepository repository) { this.repository = repository; }

    @Transactional
    public UUID saveBacktest(BreadthFilteredMomentumBacktestRequest request, BreadthFilteredMomentumBacktestResult result,
                              long qualifyingSnapshotSampleCount) {
        RiskAdjustedMomentumBacktestResult baseline = result.baseline();
        RiskAdjustedMomentumBacktestResult filtered = result.breadthFiltered();
        BreadthBacktestRun run = new BreadthBacktestRun();
        run.setRunType(BreadthBacktestRunType.BACKTEST);
        run.setStatus(BreadthBacktestStatus.COMPLETED);
        run.setStartDate(request.startDate()); run.setEndDate(request.endDate());
        run.setInitialCapital(request.initialCapital()); run.setEntryRank(request.entryRank()); run.setRetentionRank(request.retentionRank());
        run.setBenchmark(request.benchmark());
        run.setBreadthUniverse(result.breadthUniverse()); run.setBreadthEntryMode(result.breadthEntryMode());
        run.setBreadthScoreCutoff(result.breadthScoreCutoff());
        run.setMethodology(result.methodology()); run.setScoreConfigurationVersion(result.representativeScoreConfigurationVersion());
        run.setBaselineTotalReturn(baseline.totalReturn()); run.setBaselineCagr(baseline.cagr());
        run.setBaselineSharpeRatio(baseline.sharpeRatio()); run.setBaselineMaximumDrawdown(baseline.maximumDrawdown());
        run.setFilteredTotalReturn(filtered.totalReturn()); run.setFilteredCagr(filtered.cagr());
        run.setFilteredSharpeRatio(filtered.sharpeRatio()); run.setFilteredMaximumDrawdown(filtered.maximumDrawdown());
        run.setSampleCount((int) qualifyingSnapshotSampleCount);
        run.setResult(new BreadthBacktestRunResult(result, null, null));
        return repository.save(run).getId();
    }

    @Transactional
    public UUID saveSensitivity(BreadthThresholdSensitivityRequest request, BreadthThresholdSensitivityResult result) {
        BreadthBacktestRun run = new BreadthBacktestRun();
        run.setRunType(BreadthBacktestRunType.SENSITIVITY);
        run.setStatus(BreadthBacktestStatus.COMPLETED);
        run.setStartDate(request.startDate()); run.setEndDate(request.endDate());
        run.setInitialCapital(request.initialCapital()); run.setEntryRank(request.entryRank()); run.setRetentionRank(request.retentionRank());
        run.setBenchmark(request.benchmark());
        run.setBreadthUniverse(request.universes().getFirst());
        run.setBreadthEntryMode(null); run.setBreadthScoreCutoff(null);
        run.setMethodology(org.factor_investing.quant_strategy.strategies.market_breadth.BreadthMethodology.CURRENT_CONSTITUENTS);
        run.setScoreConfigurationVersion(0);
        run.setSampleCount(result.cells().size());
        run.setResult(new BreadthBacktestRunResult(null, result, null));
        return repository.save(run).getId();
    }

    @Transactional(readOnly = true)
    public BreadthBacktestRunResult get(UUID id) {
        return getEntity(id).getResult();
    }

    @Transactional(readOnly = true)
    public BreadthBacktestRun getEntity(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Breadth backtest execution not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<BreadthBacktestRunSummary> history(BreadthBacktestRunType runType) {
        List<BreadthBacktestRunRepository.SummaryView> rows = runType == null
                ? repository.findAllByOrderByCreatedAtDesc() : repository.findByRunTypeOrderByCreatedAtDesc(runType);
        return rows.stream().map(row -> new BreadthBacktestRunSummary(row.getId(), row.getCreatedAt(), row.getRunType(),
                row.getStatus(), row.getStartDate(), row.getEndDate(), row.getBreadthUniverse(), row.getBreadthEntryMode(),
                row.getBreadthScoreCutoff(), row.getBaselineTotalReturn(), row.getFilteredTotalReturn(),
                row.getFilteredSharpeRatio(), row.getFilteredMaximumDrawdown(), row.getSampleCount())).toList();
    }

    public BreadthBacktestRunDetail toDetail(BreadthBacktestRun run) {
        BreadthBacktestRunResult result = run.getResult();
        return new BreadthBacktestRunDetail(run.getId(), run.getCreatedAt(), run.getRunType(), run.getStatus(),
                result == null ? null : result.backtest(), result == null ? null : result.sensitivity());
    }
}
