package org.factor_investing.quant_strategy.service;

import jakarta.annotation.PreDestroy;
import org.factor_investing.quant_strategy.model.PriceImportRun;
import org.factor_investing.quant_strategy.repository.PriceImportRunRepository;
import org.springframework.context.event.EventListener;
import java.time.Instant;
import org.factor_investing.quant_strategy.model.PriceFrequencey;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class PriceUpdateJobService {
    public record Job(String id, String source, PriceFrequencey timeFrame, String status, String message, int processed, int total, int saved, String failedSymbols) {}

    private final PriceDataService prices;
    private final PriceImportRunRepository runs;
    private final CloudRunPriceJobLauncher launcher;
    private final ThreadLocal<String> currentRun = new ThreadLocal<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<String, Job> jobs = new LinkedHashMap<>();

    public PriceUpdateJobService(PriceDataService prices, PriceImportRunRepository runs, CloudRunPriceJobLauncher launcher) {
        this.prices = prices;
        this.runs = runs;
        this.launcher = launcher;
    }

    public synchronized Job start(String source, PriceFrequencey timeFrame) {
        if (!source.equals("stock-Price") && !source.equals("ETF-Price") && !source.equals("index-Price")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown price source");
        }
        for (Job localJob : jobs.values()) {
            Job job = get(localJob.id());
            jobs.put(job.id(), job);
            if (job.source().equals(source) && job.timeFrame() == timeFrame && active(job)) {
                return job;
            }
        }
        // Keep recent results without allowing completed jobs to grow indefinitely.
        if (jobs.size() >= 100) {
            jobs.values().removeIf(job -> !active(job));
        }
        Job job = new Job(UUID.randomUUID().toString(), source, timeFrame, "QUEUED", "Update queued", 0, 0, 0, "");
        persist(job);
        jobs.put(job.id(), job);
        if (launcher.enabled()) {
            try {
                launcher.launch(job);
            } catch (Exception exception) {
                if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
                update(job, "FAILED", "Could not confirm Cloud Run dispatch; check execution logs before retrying.");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Cloud Run job dispatch failed", exception);
            }
        } else {
            executor.submit(() -> run(job));
        }
        return job;
    }

    public synchronized Job get(String id) {
        return runs.findById(id).map(this::toJob).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown update job"));
    }

    /** Used by the dedicated Cloud Run Job process; failures result in a nonzero exit. */
    public Job runBlocking(String source, PriceFrequencey timeFrame) {
        return runBlocking(source, timeFrame, null);
    }

    public Job runBlocking(String source, PriceFrequencey timeFrame, String runId) {
        if (!source.equals("stock-Price") && !source.equals("ETF-Price") && !source.equals("index-Price")) {
            throw new IllegalArgumentException("Unknown price source: " + source);
        }
        Job job = new Job(UUID.randomUUID().toString(), source, timeFrame, "QUEUED", "Update queued", 0, 0, 0, "");
        if (runId != null && !runId.isBlank()) {
            job = get(runId);
            if (!job.source().equals(source) || job.timeFrame() != timeFrame) {
                throw new IllegalArgumentException("Import run parameters do not match");
            }
            if (job.status().equals("SUCCEEDED")) return job;
        } else {
            persist(job);
        }
        run(job);
        Job result = get(job.id());
        if (!result.status().equals("SUCCEEDED")) throw new IllegalStateException("Import failed; run id=" + job.id());
        return result;
    }

    private boolean active(Job job) {
        return job.status().equals("QUEUED") || job.status().equals("RUNNING");
    }

    private void run(Job job) {
        currentRun.set(job.id());
        try {
            update(job, "RUNNING", "Updating prices");
            String result = switch (job.source()) {
                case "stock-Price" -> prices.saveOrUpdateStockPriceData(job.timeFrame());
                case "ETF-Price" -> prices.saveOrUpdateETFPriceData(job.timeFrame());
                case "index-Price" -> prices.saveOrUpdateIndexPriceData(job.timeFrame());
                default -> throw new IllegalArgumentException("Unknown price source");
            };
            update(job, "SUCCEEDED", result);
        } catch (Exception exception) {
            Job checkpoint = get(job.id());
            update(job, "FAILED", "Price update failed after saving " + checkpoint.saved()
                    + " symbols. " + (checkpoint.failedSymbols().isEmpty() ? "Check server logs for details."
                    : "Failed symbols: " + checkpoint.failedSymbols()));
            org.slf4j.LoggerFactory.getLogger(getClass()).error("Price update job {} failed", job.id(), exception);
        } finally {
            currentRun.remove();
        }
    }

    @EventListener
    public void onProgress(PriceImportProgress progress) {
        String id = currentRun.get();
        if (id == null) return;
        PriceImportRun run = runs.findById(id).orElseThrow();
        run.setProcessed(progress.processed());
        run.setTotal(progress.total());
        run.setSaved(progress.saved());
        run.setFailedSymbols(String.join(",", progress.failedSymbols()));
        run.setUpdatedAt(Instant.now());
        runs.save(run);
    }

    private synchronized void update(Job job, String status, String message) {
        Job previous = get(job.id());
        Job updated = new Job(job.id(), job.source(), job.timeFrame(), status, message,
                previous.processed(), previous.total(), previous.saved(), previous.failedSymbols());
        persist(updated);
        jobs.put(job.id(), updated);
    }

    private void persist(Job job) {
        PriceImportRun run = new PriceImportRun();
        run.setId(job.id());
        run.setSource(job.source());
        run.setTimeFrame(job.timeFrame());
        run.setStatus(job.status());
        run.setMessage(job.message());
        run.setProcessed(job.processed());
        run.setTotal(job.total());
        run.setSaved(job.saved());
        run.setFailedSymbols(job.failedSymbols());
        run.setUpdatedAt(Instant.now());
        runs.save(run);
    }

    private Job toJob(PriceImportRun run) {
        return new Job(run.getId(), run.getSource(), run.getTimeFrame(), run.getStatus(), run.getMessage(),
                run.getProcessed(), run.getTotal(), run.getSaved(), run.getFailedSymbols());
    }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
    }
}
