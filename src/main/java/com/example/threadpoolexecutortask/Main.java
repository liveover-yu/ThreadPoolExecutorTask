package com.example.threadpoolexecutortask;

import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ThreadPoolExecutorApplication {
    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(3);

        for (int i = 0; i < 3; i++) {
            int taskId = i;

            threadPoolExecutor.execute(() -> {
                System.out.println(Thread.currentThread().getName() + "正在执行任务" + taskId);

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                System.out.println(Thread.currentThread().getName() + " 完成任务 " + taskId);
            });
        }

        Thread.sleep(2000);
        threadPoolExecutor.shutdown();

        System.out.println("主线程结束");
    }
}
