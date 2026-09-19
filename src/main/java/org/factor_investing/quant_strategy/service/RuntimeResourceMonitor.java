package org.factor_investing.quant_strategy.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.util.Locale;

/**
 * Periodically records resource usage in application logs. This is deliberately
 * dependency-free so it works in both local Java and cloud container runtimes.
 */
@Component
@Slf4j
public class RuntimeResourceMonitor {

    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    private final java.lang.management.OperatingSystemMXBean operatingSystemBean =
            ManagementFactory.getOperatingSystemMXBean();

    @Value("${runtime.monitor.enabled:true}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${runtime.monitor.interval-ms:60000}", initialDelayString = "${runtime.monitor.initial-delay-ms:30000}")
    public void logResourceUsage() {
        if (!enabled) {
            return;
        }

        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryBean.getNonHeapMemoryUsage();
        Runtime runtime = Runtime.getRuntime();

        long usedHeap = heap.getUsed();
        long maxHeap = heap.getMax() > 0 ? heap.getMax() : runtime.maxMemory();
        long committedHeap = heap.getCommitted();
        double processCpu = -1;
        double systemCpu = -1;
        long totalPhysicalMemory = -1;
        long freePhysicalMemory = -1;

        if (operatingSystemBean instanceof com.sun.management.OperatingSystemMXBean sunOs) {
            processCpu = sunOs.getProcessCpuLoad();
            systemCpu = sunOs.getCpuLoad();
            totalPhysicalMemory = sunOs.getTotalMemorySize();
            freePhysicalMemory = sunOs.getFreeMemorySize();
        }

        log.info("RUNTIME_RESOURCE_USAGE heapUsed={}MB heapCommitted={}MB heapMax={}MB " +
                        "nonHeapUsed={}MB processCpu={} systemCpu={} " +
                        "physicalMemory={}MB physicalFree={}MB threads={} peakThreads={} processors={}",
                megabytes(usedHeap), megabytes(committedHeap), megabytes(maxHeap),
                megabytes(nonHeap.getUsed()),
                percent(processCpu), percent(systemCpu),
                totalPhysicalMemory < 0 ? "n/a" : megabytes(totalPhysicalMemory),
                freePhysicalMemory < 0 ? "n/a" : megabytes(freePhysicalMemory),
                threadBean.getThreadCount(), threadBean.getPeakThreadCount(),
                runtime.availableProcessors());
    }

    private static long megabytes(long bytes) {
        return bytes < 0 ? -1 : bytes / (1024 * 1024);
    }

    private static String percent(double value) {
        return value < 0 ? "n/a" : String.format(Locale.ROOT, "%.1f%%", value * 100);
    }
}