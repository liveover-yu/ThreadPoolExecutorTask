package com.example.threadpoolexecutortask;

@FunctionalInterface
public interface RejectPolicy {
    void reject(Runnable task);
}
