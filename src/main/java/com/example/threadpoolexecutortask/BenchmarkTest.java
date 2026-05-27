package com.example.threadpoolexecutortask;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BenchmarkTest {
    private static final int CORE_POOL_SIZE = 2;
    private static final int MAXIMUM_POOL_SIZE = 4;
    private static final int QUEUE_CAPACITY = 100;
    private static final long KEEP_ALIVE_MILLIS = 3000;
    private static final int TOTAL_TASKS = 1000;
    private static final int ROUNDS = 5;
    private static final int CPU_LOOP_COUNT = 20_000_000;
    private static final int IO_SLEEP_MILLIS = 100;
    private static volatile long blackhole;

    public static void main(String[] args) throws InterruptedException {
        runIoBenchmark();
        System.out.println();
        runCpuBenchmark();
    }

    private static void runIoBenchmark() throws InterruptedException {
        System.out.println("=== I/O等待型任务对比测试 ===");
        System.out.println("任务类型: I/O等待型");
        System.out.println("任务内容: Thread.sleep(" + IO_SLEEP_MILLIS + "ms)");
        printCommonConfig();

        System.out.println("| 线程池实现 | 轮次 | 完成任务数 | 拒绝任务数 | 耗时(ms) | 是否正常结束 |");
        System.out.println("|---|---:|---:|---:|---:|---|");

        long customTotalCost = 0;
        long jucTotalCost = 0;

        for (int round = 1; round <= ROUNDS; round++) {
            TestResult customResult = runCustomThreadPool(BenchmarkTest::runIoTask);
            customTotalCost += customResult.costMillis;
            printResult("自定义线程池", round, customResult);

            TestResult jucResult = runJucThreadPool(BenchmarkTest::runIoTask);
            jucTotalCost += jucResult.costMillis;
            printResult("JUC官方线程池", round, jucResult);
        }

        System.out.println();
        System.out.println("自定义线程池平均耗时(ms): " + customTotalCost / ROUNDS);
        System.out.println("JUC官方线程池平均耗时(ms): " + jucTotalCost / ROUNDS);
        System.out.println("说明: I/O等待型任务使用 sleep 模拟等待，主要观察相同任务下两种线程池的调度表现。");
    }

    private static void runCpuBenchmark() throws InterruptedException {
        System.out.println("=== CPU密集型任务对比测试 ===");
        System.out.println("任务类型: CPU密集型");
        System.out.println("任务内容: 循环累加计算，loopCount = " + CPU_LOOP_COUNT);
        printCommonConfig();
        System.out.println("说明: CPU计算结果写入 volatile blackhole，避免计算被过度优化。");
        System.out.println();

        System.out.println("| 线程池实现 | 轮次 | 完成任务数 | 拒绝任务数 | 耗时(ms) | 是否正常结束 |");
        System.out.println("|---|---:|---:|---:|---:|---|");

        long customTotalCost = 0;
        long jucTotalCost = 0;

        for (int round = 1; round <= ROUNDS; round++) {
            TestResult customResult = runCustomThreadPool(BenchmarkTest::runCpuTask);
            customTotalCost += customResult.costMillis;
            printResult("自定义线程池", round, customResult);

            TestResult jucResult = runJucThreadPool(BenchmarkTest::runCpuTask);
            jucTotalCost += jucResult.costMillis;
            printResult("JUC官方线程池", round, jucResult);
        }

        System.out.println();
        System.out.println("自定义线程池平均耗时(ms): " + customTotalCost / ROUNDS);
        System.out.println("JUC官方线程池平均耗时(ms): " + jucTotalCost / ROUNDS);
        System.out.println("说明: CPU密集型任务耗时受CPU负载、JIT编译和线程调度影响，单次结果可能波动。");
    }

    private static void printCommonConfig() {
        System.out.println("corePoolSize: " + CORE_POOL_SIZE);
        System.out.println("maximumPoolSize: " + MAXIMUM_POOL_SIZE);
        System.out.println("queueCapacity: " + QUEUE_CAPACITY);
        System.out.println("totalTasks: " + TOTAL_TASKS);
        System.out.println("rounds: " + ROUNDS);
        System.out.println();
    }

    private static TestResult runCustomThreadPool(Runnable benchmarkTask) throws InterruptedException {
        AtomicInteger rejectedCount = new AtomicInteger(0);
        AtomicInteger finishedCount = new AtomicInteger(0);

        ThreadPoolExecutorPractice pool = new ThreadPoolExecutorPractice(
                CORE_POOL_SIZE,
                MAXIMUM_POOL_SIZE,
                QUEUE_CAPACITY,
                KEEP_ALIVE_MILLIS,
                task -> rejectedCount.incrementAndGet()
        );

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < TOTAL_TASKS; i++) {
            pool.execute(() -> {
                benchmarkTask.run();
                finishedCount.incrementAndGet();
            });
        }

        pool.shutdown();
        boolean terminated = pool.awaitTermination(60_000);
        long endTime = System.currentTimeMillis();

        return new TestResult(finishedCount.get(), rejectedCount.get(), endTime - startTime, terminated);
    }

    private static TestResult runJucThreadPool(Runnable benchmarkTask) throws InterruptedException {
        AtomicInteger rejectedCount = new AtomicInteger(0);
        AtomicInteger finishedCount = new AtomicInteger(0);

        java.util.concurrent.ThreadPoolExecutor pool = new java.util.concurrent.ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAXIMUM_POOL_SIZE,
                KEEP_ALIVE_MILLIS,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                (task, executor) -> rejectedCount.incrementAndGet()
        );

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < TOTAL_TASKS; i++) {
            pool.execute(() -> {
                benchmarkTask.run();
                finishedCount.incrementAndGet();
            });
        }

        pool.shutdown();
        boolean terminated = pool.awaitTermination(60, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();

        return new TestResult(finishedCount.get(), rejectedCount.get(), endTime - startTime, terminated);
    }

    private static void runIoTask() {
        try {
            Thread.sleep(IO_SLEEP_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void runCpuTask() {
        long result = 0;
        for (int i = 0; i < CPU_LOOP_COUNT; i++) {
            result += i;
        }

        blackhole = result;
    }

    private static void printResult(String poolName, int round, TestResult result) {
        System.out.println("| " + poolName
                + " | " + round
                + " | " + result.finishedCount
                + " | " + result.rejectedCount
                + " | " + result.costMillis
                + " | " + result.terminated
                + " |");
    }

    private static class TestResult {
        private final int finishedCount;
        private final int rejectedCount;
        private final long costMillis;
        private final boolean terminated;

        private TestResult(int finishedCount, int rejectedCount, long costMillis, boolean terminated) {
            this.finishedCount = finishedCount;
            this.rejectedCount = rejectedCount;
            this.costMillis = costMillis;
            this.terminated = terminated;
        }
    }
}
