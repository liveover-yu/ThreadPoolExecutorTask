package com.example.threadpoolexecutortask;

import java.util.List;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExecutorPractice threadPoolExecutor = new ThreadPoolExecutorPractice(
                2,
                4,
                2,
                3000,
                task -> System.out.println("任务被拒绝:" + task)
        );

        for (int i = 0; i < 10; i++) {
            int taskId = i;

            threadPoolExecutor.execute(() -> {
                System.out.println(Thread.currentThread().getName() + "正在执行任务" + taskId);

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println(Thread.currentThread().getName() + "任务被中断" + taskId);
                    return;
                }

                System.out.println(Thread.currentThread().getName() + "完成任务" + taskId);
            });
        }

        Thread.sleep(1000);
        System.out.println("高峰期后worker数量:" + threadPoolExecutor.getPoolSize());
        System.out.println("高峰期后活跃线程数量:" + threadPoolExecutor.getActiveCount());

        Thread.sleep(5000);
        System.out.println("shutdownNow前worker数量:" + threadPoolExecutor.getPoolSize());
        System.out.println("shutdownNow前活跃线程数量:" + threadPoolExecutor.getActiveCount());

        List<Runnable> notExecutedTasks = threadPoolExecutor.shutdownNow();
        System.out.println("未执行任务数量:" + notExecutedTasks.size());

        boolean terminated = threadPoolExecutor.awaitTermination(5000);
        System.out.println("线程池是否正常结束:" + terminated);

        System.out.println("主线程结束");
    }
}
