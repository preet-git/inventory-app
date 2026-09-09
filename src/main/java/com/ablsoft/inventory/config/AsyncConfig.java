package com.ablsoft.inventory.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The pool that runs imports.
 *
 * <p>Deliberately small and deliberately bounded. An import is not waiting on anything — it parses
 * and writes flat out — so more threads than this would only make several imports contend for the
 * same CPU and the same connection pool. Platform threads, not virtual ones, for the same reason:
 * virtual threads help work that blocks, and would not bound anything here.
 *
 * <p>When the queue is full the executor aborts rather than making the caller wait, which surfaces
 * to the client as a 429 instead of a request that hangs.
 */
@Configuration
public class AsyncConfig {

    public static final String IMPORT_EXECUTOR = "importExecutor";

    @Bean(IMPORT_EXECUTOR)
    public ThreadPoolTaskExecutor importExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("import-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
