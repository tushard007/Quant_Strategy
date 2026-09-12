package org.factor_investing.quant_strategy.repository;

import org.factor_investing.quant_strategy.model.RiskAdjustedMomentumBacktestRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RiskAdjustedMomentumBacktestRunRepository extends JpaRepository<RiskAdjustedMomentumBacktestRun, UUID> {
    List<SummaryView> findAllProjectedByOrderByCreatedAtDesc();

    interface SummaryView {
        UUID getId();
        Instant getCreatedAt();
        LocalDate getStartDate();
        LocalDate getEndDate();
        int getEntryRank();
        int getRetentionRank();
        String getBenchmark();
        String getRebalanceMode();
        String getStopModel();
        double getFinalValue();
        double getTotalReturn();
        double getCagr();
        double getMaximumDrawdown();
        double getSharpeRatio();
    }
}
