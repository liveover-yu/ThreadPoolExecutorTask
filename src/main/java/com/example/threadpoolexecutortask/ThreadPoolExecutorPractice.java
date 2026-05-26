package com.example.threadpoolexecutortask;

import java.util.Queue;
import java.util.LinkedList;

public class ThreadPoolExecutor {
    private final Queue<Runnable> taskQueue = new LinkedList<>();
    private final Worker[] workers;
    private volatile boolean shutdown = false;

    public ThreadPoolExecutor(int workerNum){
        workers = new Worker[workerNum];

        for(int i = 0; i < workerNum; i++){
            workers[i] = new Worker("worker"+i);
            workers[i].start();
        }
    }

    public void execute(Runnable task){
        synchronized (taskQueue){
            if(shutdown){
                throw new IllegalStateException("线程池已关闭，无法接受新任务");
            }

            taskQueue.offer(task);
            taskQueue.notifyAll();
        }
    }

    public void shutdown(){
        synchronized (taskQueue){
            shutdown = true;
            taskQueue.notifyAll();
        }
    }

    private class Worker extends Thread{
        public Worker(String name){
            super(name);
        }

        @Override
        public void run(){
            while(true){
                Runnable task;

                synchronized (taskQueue){
                    while(taskQueue.isEmpty() && !shutdown){
                        try {
                            taskQueue.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }

                    if(shutdown && taskQueue.isEmpty()){
                        return;
                    }

                    task = taskQueue.poll();
                }

                try{
                    task.run();
                } catch (Exception e){
                    System.out.println(getName() + "执行出错:" + e.getMessage());
                }
            }
        }
    }
}
