package com.example.threadpoolexecutortask;

import java.util.concurrent.atomic.AtomicInteger;

public class PressureTest {
    public static void main(String[] args) throws InterruptedException {
        capacityBoundaryTest();
        System.out.println();
        continuousPressureTest();
    }

    private static void capacityBoundaryTest() throws InterruptedException {
        System.out.println("=== 容量边界测试 ===");

        int corePoolSize = 2;
        int maximumPoolSize = 4;
        int queueCapacity = 100;
        int totalTasks = 1000;
        int taskSleepMillis = 100;

        AtomicInteger rejectedCount = new AtomicInteger(0);
        AtomicInteger startedCount = new AtomicInteger(0);
        AtomicInteger finishedCount = new AtomicInteger(0);

        ThreadPoolExecutorPractice pool = new ThreadPoolExecutorPractice(
                corePoolSize,
                maximumPoolSize,
                queueCapacity,
                3000,
                task -> rejectedCount.incrementAndGet()
        );

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalTasks; i++) {
            pool.execute(() -> {
                startedCount.incrementAndGet();

                try {
                    Thread.sleep(taskSleepMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                finishedCount.incrementAndGet();
            });
        }

        Thread.sleep(50);

        int expectedAccepted = maximumPoolSize + queueCapacity;
        int expectedBatches = (int) Math.ceil((double) expectedAccepted / maximumPoolSize);
        int expectedExecutionMillis = expectedBatches * taskSleepMillis;
        int observedPoolSize = pool.getPoolSize();
        int observedActiveCount = pool.getActiveCount();
        int rejectedBeforeShutdown = rejectedCount.get();
        int acceptedCount = totalTasks - rejectedBeforeShutdown;

        System.out.println("corePoolSize: " + corePoolSize);
        System.out.println("maximumPoolSize: " + maximumPoolSize);
        System.out.println("queueCapacity: " + queueCapacity);
        System.out.println("提交任务数: " + totalTasks);
        System.out.println("任务类型: I/O等待模拟任务");
        System.out.println("任务内容: Thread.sleep(" + taskSleepMillis + "ms)");
        System.out.println("理论最大接收任务数: " + expectedAccepted);
        System.out.println("理论执行批次数: " + expectedBatches);
        System.out.println("理论执行耗时 ms: " + expectedExecutionMillis);
        System.out.println("实际接收任务数: " + acceptedCount);
        System.out.println("提交后 worker 数量: " + observedPoolSize);
        System.out.println("提交后活跃线程数: " + observedActiveCount);
        System.out.println("拒绝任务数: " + rejectedBeforeShutdown);

        pool.shutdown();

        boolean terminated = pool.awaitTermination(30000);
        long endTime = System.currentTimeMillis();

        System.out.println("实际开始执行任务数: " + startedCount.get());
        System.out.println("实际完成任务数: " + finishedCount.get());
        System.out.println("完成 + 拒绝: " + (finishedCount.get() + rejectedCount.get()));
        System.out.println("最终 worker 数量: " + pool.getPoolSize());
        System.out.println("线程池是否正常结束: " + terminated);
        System.out.println("已接收任务完成耗时 ms: " + (endTime - startTime));

        System.out.println("校验-实际接收数等于理论接收数: " + pass(acceptedCount == expectedAccepted));
        System.out.println("校验-开始执行数等于接收数: " + pass(startedCount.get() == acceptedCount));
        System.out.println("校验-完成数等于接收数: " + pass(finishedCount.get() == acceptedCount));
        System.out.println("校验-完成数加拒绝数等于提交数: " + pass(finishedCount.get() + rejectedCount.get() == totalTasks));
        System.out.println("校验-提交后worker数量不超过最大线程数: " + pass(observedPoolSize <= maximumPoolSize));
        System.out.println("校验-线程池正常结束: " + pass(terminated));
    }

    private static void continuousPressureTest() throws InterruptedException {
        System.out.println("=== 持续压力测试 ===");

        int corePoolSize = 2;
        int maximumPoolSize = 4;
        int queueCapacity = 100;
        int totalTasks = 300;
        int taskSleepMillis = 50;
        int submitIntervalMillis = 5;
        int estimatedSubmitRate = 1000 / submitIntervalMillis;
        int estimatedWorkerCapacity = maximumPoolSize * 1000 / taskSleepMillis;

        AtomicInteger rejectedCount = new AtomicInteger(0);
        AtomicInteger startedCount = new AtomicInteger(0);
        AtomicInteger finishedCount = new AtomicInteger(0);

        ThreadPoolExecutorPractice pool = new ThreadPoolExecutorPractice(
                corePoolSize,
                maximumPoolSize,
                queueCapacity,
                3000,
                task -> rejectedCount.incrementAndGet()
        );

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalTasks; i++) {
            pool.execute(() -> {
                startedCount.incrementAndGet();

                try {
                    Thread.sleep(taskSleepMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                finishedCount.incrementAndGet();
            });

            Thread.sleep(submitIntervalMillis);
        }

        System.out.println("corePoolSize: " + corePoolSize);
        System.out.println("maximumPoolSize: " + maximumPoolSize);
        System.out.println("queueCapacity: " + queueCapacity);
        System.out.println("提交任务数: " + totalTasks);
        System.out.println("任务类型: I/O等待模拟任务");
        System.out.println("任务内容: Thread.sleep(" + taskSleepMillis + "ms)");
        System.out.println("提交间隔 ms: " + submitIntervalMillis);
        System.out.println("估算提交速率 tasks/s: " + estimatedSubmitRate);
        System.out.println("估算最大处理能力 tasks/s: " + estimatedWorkerCapacity);
        System.out.println("说明: 最大处理能力基于 sleep 等待时间估算，不代表 CPU 计算性能");
        System.out.println("说明: 提交速率高于最大处理能力，用于制造持续压力");

        int observedPoolSize = pool.getPoolSize();
        int observedActiveCount = pool.getActiveCount();
        int rejectedBeforeShutdown = rejectedCount.get();
        int acceptedCount = totalTasks - rejectedBeforeShutdown;

        System.out.println("提交后 worker 数量: " + observedPoolSize);
        System.out.println("提交后活跃线程数: " + observedActiveCount);
        System.out.println("实际接收任务数: " + acceptedCount);
        System.out.println("拒绝任务数: " + rejectedBeforeShutdown);

        pool.shutdown();

        boolean terminated = pool.awaitTermination(30000);
        long endTime = System.currentTimeMillis();

        System.out.println("实际开始执行任务数: " + startedCount.get());
        System.out.println("实际完成任务数: " + finishedCount.get());
        System.out.println("完成 + 拒绝: " + (finishedCount.get() + rejectedCount.get()));
        System.out.println("最终 worker 数量: " + pool.getPoolSize());
        System.out.println("线程池是否正常结束: " + terminated);
        System.out.println("已接收任务完成耗时 ms: " + (endTime - startTime));

        System.out.println("校验-开始执行数等于接收数: " + pass(startedCount.get() == acceptedCount));
        System.out.println("校验-完成数等于接收数: " + pass(finishedCount.get() == acceptedCount));
        System.out.println("校验-完成数加拒绝数等于提交数: " + pass(finishedCount.get() + rejectedCount.get() == totalTasks));
        System.out.println("校验-提交后worker数量不超过最大线程数: " + pass(observedPoolSize <= maximumPoolSize));
        System.out.println("校验-线程池正常结束: " + pass(terminated));
    }

    private static String pass(boolean result) {
        return result ? "PASS" : "FAIL";
    }
}
