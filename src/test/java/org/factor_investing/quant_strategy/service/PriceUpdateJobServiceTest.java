package org.factor_investing.quant_strategy.service;

import org.factor_investing.quant_strategy.model.PriceFrequencey;
import org.junit.jupiter.api.Test;
import org.factor_investing.quant_strategy.model.PriceImportRun;
import org.factor_investing.quant_strategy.repository.PriceImportRunRepository;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PriceUpdateJobServiceTest {
    @Test
    void returnsBeforeCompletionAndReconnectsToActiveJob() throws Exception {
        PriceDataService prices = mock(PriceDataService.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(prices.saveOrUpdateStockPriceData(PriceFrequencey.DAILY)).thenAnswer(call -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return "Saved";
        });
        PriceUpdateJobService jobs = new PriceUpdateJobService(prices, repository(), mock(CloudRunPriceJobLauncher.class));
        try {
            var job = jobs.start("stock-Price", PriceFrequencey.DAILY);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals(job.id(), jobs.start("stock-Price", PriceFrequencey.DAILY).id());
            assertEquals("RUNNING", jobs.get(job.id()).status());
            release.countDown();
            awaitStatus(jobs, job.id(), "SUCCEEDED");
            assertEquals("Saved", jobs.get(job.id()).message());
            verify(prices, times(1)).saveOrUpdateStockPriceData(PriceFrequencey.DAILY);
        } finally {
            release.countDown();
            jobs.close();
        }
    }

    @Test
    void exposesFailedJobs() throws Exception {
        PriceDataService prices = mock(PriceDataService.class);
        when(prices.saveOrUpdateETFPriceData(PriceFrequencey.WEEKLY)).thenThrow(new RuntimeException("upstream failure"));
        PriceUpdateJobService jobs = new PriceUpdateJobService(prices, repository(), mock(CloudRunPriceJobLauncher.class));
        try {
            var job = jobs.start("ETF-Price", PriceFrequencey.WEEKLY);
            awaitStatus(jobs, job.id(), "FAILED");
        } finally {
            jobs.close();
        }
    }

    @Test
    void readsCompletedProgressFromAnotherServiceInstance() {
        PriceImportRunRepository repository = repository();
        PriceDataService prices = mock(PriceDataService.class);
        {
            PriceUpdateJobService first = new PriceUpdateJobService(prices, repository, mock(CloudRunPriceJobLauncher.class));
            PriceUpdateJobService second = new PriceUpdateJobService(prices, repository, mock(CloudRunPriceJobLauncher.class));
            try {
                var result = first.runBlocking("stock-Price", PriceFrequencey.DAILY);
                assertEquals("SUCCEEDED", second.get(result.id()).status());
            } finally {
                first.close();
                second.close();
            }
        }
    }

    @Test
    void cloudDispatchReturnsQueuedJobAndWorkerPersistsProgressWithSameId() throws Exception {
        PriceImportRunRepository repository = repository();
        PriceDataService prices = mock(PriceDataService.class);
        CloudRunPriceJobLauncher launcher = mock(CloudRunPriceJobLauncher.class);
        when(launcher.enabled()).thenReturn(true);
        PriceUpdateJobService web = new PriceUpdateJobService(prices, repository, launcher);
        PriceUpdateJobService worker = new PriceUpdateJobService(prices, repository, mock(CloudRunPriceJobLauncher.class));
        try {
            var queued = web.start("stock-Price", PriceFrequencey.DAILY);
            verify(launcher).launch(queued);
            verifyNoInteractions(prices);
            assertEquals("QUEUED", web.get(queued.id()).status());
            when(prices.saveOrUpdateStockPriceData(PriceFrequencey.DAILY)).thenAnswer(call -> {
                worker.onProgress(new PriceImportProgress(25, 25, 24, java.util.List.of()));
                return "Saved";
            });
            worker.runBlocking("stock-Price", PriceFrequencey.DAILY, queued.id());
            var finished = web.get(queued.id());
            assertEquals("SUCCEEDED", finished.status());
            assertEquals(25, finished.processed());
            assertEquals(24, finished.saved());
            // Duplicate Cloud Run delivery of an already successful run is a no-op.
            worker.runBlocking("stock-Price", PriceFrequencey.DAILY, queued.id());
            verify(prices, times(1)).saveOrUpdateStockPriceData(PriceFrequencey.DAILY);
        } finally {
            web.close(); worker.close();
        }
    }

    private PriceImportRunRepository repository() {
        PriceImportRunRepository repository = mock(PriceImportRunRepository.class);
        var rows = new ConcurrentHashMap<String, PriceImportRun>();
        when(repository.save(any())).thenAnswer(call -> {
            PriceImportRun row = call.getArgument(0);
            rows.put(row.getId(), row);
            return row;
        });
        when(repository.findById(anyString())).thenAnswer(call -> Optional.ofNullable(rows.get(call.getArgument(0))));
        return repository;
    }

    private void awaitStatus(PriceUpdateJobService jobs, String id, String status) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!jobs.get(id).status().equals(status) && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(status, jobs.get(id).status());
    }
}
