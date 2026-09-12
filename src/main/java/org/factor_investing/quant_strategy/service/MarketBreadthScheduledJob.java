package org.factor_investing.quant_strategy.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@Slf4j
@ConditionalOnProperty(name = "market-breadth.schedule.enabled", havingValue = "true", matchIfMissing = true)
public class MarketBreadthScheduledJob {
    private final MarketBreadthPipelineService pipelineService;

    public MarketBreadthScheduledJob(MarketBreadthPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @Scheduled(cron = "${market-breadth.eod.cron:0 30 18 * * MON-FRI}", zone = "Asia/Kolkata")
    public void runEndOfDayBreadth() {
        try {
            log.info("Starting ordered EOD market-breadth pipeline");
            pipelineService.runEndOfDay(LocalDate.now());
        } catch (Exception exception) {
            log.error("EOD market-breadth pipeline failed", exception);
        }
    }
}
