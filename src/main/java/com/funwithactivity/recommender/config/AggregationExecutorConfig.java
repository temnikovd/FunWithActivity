package com.funwithactivity.recommender.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AggregationExecutorConfig {

    /**
     * Scatter-gather calls out to a handful of I/O-bound providers per request -
     * a virtual-thread-per-task executor avoids sizing a fixed pool for that.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService providerCallExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
