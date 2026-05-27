package com.example.threadpoolexecutortask;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CpuSustainedBenchmark {
    private static final int CPU_PHYSICAL_CORES = 16;
    private static final int CPU_LOGICAL_PROCESSORS = 32;
    private static final int CORE_POOL_SIZE = 16;
    private static final int MAXIMUM_POOL_SIZE = 16;
    private static final int QUEUE_CAPACITY = 10_000;
    private static final int BACKLOG_LIMIT = 2_000;
    private static final long KEEP_ALIVE_MILLIS = 3000;
    private static final int CPU_LOOP_COUNT = 200_000;
    private static final int DEFAULT_DURATION_SECONDS = 300;
    private static final int SAMPLE_INTERVAL_MILLIS = 1000;
    private static final Path OUTPUT_FILE = Path.of("benchmark-results", "cpu-sustained.csv");
    private static volatile double blackhole;

    public static void main(String[] args) throws Exception {
        int durationSeconds = parseDurationSeconds(args);

        Files.createDirectories(OUTPUT_FILE.getParent());

        try (BufferedWriter writer = Files.newBufferedWriter(OUTPUT_FILE, StandardCharsets.UTF_8)) {
            writer.write("second,poolType,submitted,accepted,finished,rejected,throughput,poolSize,activeCount");
            writer.newLine();

            printEnvironment(durationSeconds);
            runCustomBenchmark(durationSeconds, writer);
            runJucBenchmark(durationSeconds, writer);
        }

        System.out.println("CSV file: " + OUTPUT_FILE.toAbsolutePath());
        System.out.println("Run scripts/plot_benchmark.py to generate charts.");
    }

    private static int parseDurationSeconds(String[] args) {
        if (args.length == 0) {
            return DEFAULT_DURATION_SECONDS;
        }

        return Integer.parseInt(args[0]);
    }

    private static void printEnvironment(int durationSeconds) {
        System.out.println("=== CPU sustained benchmark ===");
        System.out.println("CPU: AMD Ryzen 9 8940HX with Radeon Graphics");
        System.out.println("physicalCores: " + CPU_PHYSICAL_CORES);
        System.out.println("logicalProcessors: " + CPU_LOGICAL_PROCESSORS);
        System.out.println("corePoolSize: " + CORE_POOL_SIZE);
        System.out.println("maximumPoolSize: " + MAXIMUM_POOL_SIZE);
        System.out.println("queueCapacity: " + QUEUE_CAPACITY);
        System.out.println("backlogLimit: " + BACKLOG_LIMIT);
        System.out.println("durationSeconds: " + durationSeconds);
        System.out.println("sampleIntervalMillis: " + SAMPLE_INTERVAL_MILLIS);
        System.out.println("cpuLoopCount: " + CPU_LOOP_COUNT);
        System.out.println("cpuTask: Math.sin / Math.cos / Math.sqrt / Math.abs");
        System.out.println();
    }

    private static void runCustomBenchmark(int durationSeconds, BufferedWriter writer) throws Exception {
        AtomicInteger submittedCount = new AtomicInteger(0);
        AtomicInteger finishedCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        ThreadPoolExecutorPractice pool = new ThreadPoolExecutorPractice(
                CORE_POOL_SIZE,
                MAXIMUM_POOL_SIZE,
                QUEUE_CAPACITY,
                KEEP_ALIVE_MILLIS,
                task -> rejectedCount.incrementAndGet()
        );

        runBenchmarkLoop(
                "custom",
                durationSeconds,
                writer,
                submittedCount,
                finishedCount,
                rejectedCount,
                () -> pool.execute(() -> {
                    calcTask(CPU_LOOP_COUNT);
                    finishedCount.incrementAndGet();
                }),
                pool::getPoolSize,
                pool::getActiveCount
        );

        pool.shutdown();
        pool.awaitTermination(120_000);
    }

    private static void runJucBenchmark(int durationSeconds, BufferedWriter writer) throws Exception {
        AtomicInteger submittedCount = new AtomicInteger(0);
        AtomicInteger finishedCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAXIMUM_POOL_SIZE,
                KEEP_ALIVE_MILLIS,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                (task, executor) -> rejectedCount.incrementAndGet()
        );

        runBenchmarkLoop(
                "juc",
                durationSeconds,
                writer,
                submittedCount,
                finishedCount,
                rejectedCount,
                () -> pool.execute(() -> {
                    calcTask(CPU_LOOP_COUNT);
                    finishedCount.incrementAndGet();
                }),
                pool::getPoolSize,
                pool::getActiveCount
        );

        pool.shutdown();
        pool.awaitTermination(120, TimeUnit.SECONDS);
    }

    private static void runBenchmarkLoop(
            String poolType,
            int durationSeconds,
            BufferedWriter writer,
            AtomicInteger submittedCount,
            AtomicInteger finishedCount,
            AtomicInteger rejectedCount,
            ThrowingRunnable submitTask,
            IntSupplier poolSizeSupplier,
            IntSupplier activeCountSupplier
    ) throws Exception {
        System.out.println("Start benchmark: " + poolType);

        long startTime = System.currentTimeMillis();
        long endTime = startTime + durationSeconds * 1000L;
        long nextSampleTime = startTime + SAMPLE_INTERVAL_MILLIS;
        int lastFinishedCount = 0;
        int second = 0;

        while (System.currentTimeMillis() < endTime) {
            long now = System.currentTimeMillis();

            while (now >= nextSampleTime && nextSampleTime <= endTime) {
                second++;
                int currentFinishedCount = finishedCount.get();
                int throughput = currentFinishedCount - lastFinishedCount;
                lastFinishedCount = currentFinishedCount;

                writeSample(
                        writer,
                        second,
                        poolType,
                        submittedCount.get(),
                        currentFinishedCount,
                        rejectedCount.get(),
                        throughput,
                        poolSizeSupplier.getAsInt(),
                        activeCountSupplier.getAsInt()
                );

                nextSampleTime += SAMPLE_INTERVAL_MILLIS;
            }

            int acceptedCount = submittedCount.get() - rejectedCount.get();
            int backlog = acceptedCount - finishedCount.get();
            if (backlog >= BACKLOG_LIMIT) {
                Thread.sleep(1);
                continue;
            }

            submitTask.run();
            submittedCount.incrementAndGet();
        }

        while (second < durationSeconds) {
            second++;
            int currentFinishedCount = finishedCount.get();
            int throughput = currentFinishedCount - lastFinishedCount;
            lastFinishedCount = currentFinishedCount;

            writeSample(
                    writer,
                    second,
                    poolType,
                    submittedCount.get(),
                    currentFinishedCount,
                    rejectedCount.get(),
                    throughput,
                    poolSizeSupplier.getAsInt(),
                    activeCountSupplier.getAsInt()
            );
        }

        System.out.println("Stop submitting: " + poolType
                + ", submitted=" + submittedCount.get()
                + ", accepted=" + (submittedCount.get() - rejectedCount.get())
                + ", finished=" + finishedCount.get()
                + ", rejected=" + rejectedCount.get());
    }

    private static void writeSample(
            BufferedWriter writer,
            int second,
            String poolType,
            int submitted,
            int finished,
            int rejected,
            int throughput,
            int poolSize,
            int activeCount
    ) throws IOException {
        int accepted = submitted - rejected;

        writer.write(second
                + "," + poolType
                + "," + submitted
                + "," + accepted
                + "," + finished
                + "," + rejected
                + "," + throughput
                + "," + poolSize
                + "," + activeCount);
        writer.newLine();
        writer.flush();
    }

    private static void calcTask(int loopCount) {
        double value = 0.0;
        for (int i = 0; i < loopCount; i++) {
            double x = i * 0.0007;
            value += Math.sin(x) * Math.cos(x) + Math.sqrt(Math.abs(x));
        }
        blackhole = value;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface IntSupplier {
        int getAsInt();
    }
}
