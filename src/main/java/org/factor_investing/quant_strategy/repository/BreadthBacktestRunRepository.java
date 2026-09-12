package org.factor_investing.quant_strategy.repository;

import org.factor_investing.quant_strategy.model.BreadthBacktestRun;
import org.factor_investing.quant_strategy.model.BreadthBacktestRunType;
import org.factor_investing.quant_strategy.model.BreadthBacktestStatus;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.strategies.breadth_backtest.BreadthEntryMode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface BreadthBacktestRunRepository extends JpaRepository<BreadthBacktestRun, UUID> {
    List<SummaryView> findByRunTypeOrderByCreatedAtDesc(BreadthBacktestRunType runType);

    List<SummaryView> findAllByOrderByCreatedAtDesc();

    interface SummaryView {
        UUID getId();
        Instant getCreatedAt();
        BreadthBacktestRunType getRunType();
        BreadthBacktestStatus getStatus();
        LocalDate getStartDate();
        LocalDate getEndDate();
        NiftyIndexName getBreadthUniverse();
        BreadthEntryMode getBreadthEntryMode();
        Double getBreadthScoreCutoff();
        Double getBaselineTotalReturn();
        Double getFilteredTotalReturn();
        Double getFilteredSharpeRatio();
        Double getFilteredMaximumDrawdown();
        Integer getSampleCount();
    }
}
