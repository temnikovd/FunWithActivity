package com.funwithactivity.recommender.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AggregationExecutorConfig {

    /** Executor for parallel I/O-bound provider calls; avoids sizing a fixed pool. */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService providerCallExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
