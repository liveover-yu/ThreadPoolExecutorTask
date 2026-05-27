package com.example.threadpoolexecutortask;

import java.util.HashSet;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

public class ThreadPoolExecutorPractice {
    private final Queue<Runnable> taskQueue = new LinkedList<>();
    private final Set<Worker> workers = new HashSet<>();
    private volatile boolean shutdown = false;
    private final int corePoolSize;
    private final int maximumPoolSize;
    private final int queueCapacity;
    private final RejectPolicy rejectPolicy;
    private final long keepAliveMillis;
    private int activeCount = 0;

    public ThreadPoolExecutorPractice(
            int corePoolSize,
            int maximumPoolSize,
            int queueCapacity,
            long keepAliveMillis,
            RejectPolicy rejectPolicy
    ){
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.queueCapacity = queueCapacity;
        this.keepAliveMillis = keepAliveMillis;
        this.rejectPolicy = rejectPolicy;
    }

    public void execute(Runnable task){
        synchronized (taskQueue){
            //线程池shutdown了，拒绝任务
            if(shutdown){
                rejectPolicy.reject(task);
                return;
            }

            //worker数小于核心池数，添加worker直接执行
            if(workers.size() < corePoolSize){
                addWorker(task,true);
                return;
            }

            //大于核心池数，但队列没满，放进队列
            if(taskQueue.size() < queueCapacity){
                taskQueue.offer(task);
                taskQueue.notifyAll();
                return;
            }

            //worker数小于最大池容量，新建非核心worker直接执行
            if(workers.size() < maximumPoolSize){
                addWorker(task,false);
                return;
            }

            //拒绝
            rejectPolicy.reject(task);
        }
    }

    private void addWorker(Runnable firstTask,boolean coreWorker){
        Worker worker = new Worker("Worker "+workers.size(), firstTask,  coreWorker);
        workers.add(worker);
        worker.start();
    }

    private void removeWorker(Worker worker){
        synchronized (taskQueue){
            workers.remove(worker);
            taskQueue.notifyAll();
        }
    }

    public int getPoolSize(){
        synchronized (taskQueue){
            return workers.size();
        }
    }

    public int getActiveCount(){
        synchronized (taskQueue){
            return activeCount;
        }
    }

    public boolean awaitTermination(long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;

        synchronized (taskQueue){
            while(!workers.isEmpty()){
                long remaining = deadline - System.currentTimeMillis();

                if(remaining <= 0){
                    return false;
                }

                taskQueue.wait(remaining);
            }

            return true;
        }
    }

    public void shutdown(){
        synchronized (taskQueue){
            shutdown = true;
            taskQueue.notifyAll();
        }
    }

    public List<Runnable> shutdownNow(){
        synchronized (taskQueue){
            shutdown = true;

            List<Runnable> noExecutorTasks = new ArrayList<>(taskQueue);
            taskQueue.clear();

            for(Worker worker : workers){
                worker.interrupt();
            }

            taskQueue.notifyAll();

            return noExecutorTasks;
        }
    }

    private class Worker extends Thread{
        private Runnable firstTask;
        private final boolean coreWorker;

        public Worker(String name, Runnable firstTask, boolean coreWorker){
            super(name);
            this.firstTask = firstTask;
            this.coreWorker = coreWorker;
        }

        @Override
        public void run(){
            Runnable task = firstTask;
            firstTask = null;

            while(true){
                if(task == null){
                    synchronized(taskQueue){
                        while(taskQueue.isEmpty() && !shutdown){
                            try {
                                if(coreWorker){
                                    taskQueue.wait();
                                }else {
                                    taskQueue.wait(keepAliveMillis);

                                    if(taskQueue.isEmpty()){
                                        removeWorker(this);
                                        return;
                                    }
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                System.out.println(Thread.currentThread().getName() + "任务被中断");
                                removeWorker(this);
                                return;
                            }
                        }

                        if(shutdown && taskQueue.isEmpty()){
                            removeWorker(this);
                            return;
                        }

                        task = taskQueue.poll();
                    }
                }

                synchronized(taskQueue){
                    activeCount++;
                }

                try{
                    task.run();
                } catch(Exception e){
                    System.out.println(getName() + "执行任务出错:" + e.getMessage());
                } finally {
                    synchronized(taskQueue){
                        activeCount--;
                        taskQueue.notifyAll();
                    }

                    task  = null;
                }
            }
        }
    }
}
