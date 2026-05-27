package com.example.threadpoolexecutortask;

import java.util.concurrent.atomic.AtomicInteger;

public class PressureTest {
    public static void main(String[] args) throws InterruptedException {
        AtomicInteger rejectedCount = new AtomicInteger(0);
        AtomicInteger startedCount = new AtomicInteger(0);
        AtomicInteger finishedCount = new AtomicInteger(0);

        ThreadPoolExecutorPractice pool = new ThreadPoolExecutorPractice(
                2,
                4,
                100,
                3000,
                task -> rejectedCount.incrementAndGet()
        );

        int totalTasks = 1000;

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalTasks; i++) {
            pool.execute(() -> {
                startedCount.incrementAndGet();

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                finishedCount.incrementAndGet();
            });
        }

        System.out.println("提交任务数: " + totalTasks);
        System.out.println("提交后 worker 数量: " + pool.getPoolSize());
        System.out.println("提交后活跃线程数: " + pool.getActiveCount());
        System.out.println("拒绝任务数: " + rejectedCount.get());

        pool.shutdown();

        boolean terminated = pool.awaitTermination(30000);

        long endTime = System.currentTimeMillis();

        System.out.println("实际开始执行任务数: " + startedCount.get());
        System.out.println("实际完成任务数: " + finishedCount.get());
        System.out.println("最终 worker 数量: " + pool.getPoolSize());
        System.out.println("线程池是否正常结束: " + terminated);
        System.out.println("总耗时 ms: " + (endTime - startTime));
    }
}
