package com.deliveryapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Dedicated thread pool for async SMS sending.
     * Isolated from Spring's default executor so slow BMS responses
     * never affect the rest of the application.
     */
    @Bean(name = "smsTaskExecutor")
    public Executor smsTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2); // 2 threads always alive
        executor.setMaxPoolSize(5); // burst up to 5 under load
        executor.setQueueCapacity(100); // queue up to 100 pending SMS tasks
        executor.setThreadNamePrefix("sms-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30); // drain queue gracefully on shutdown
        executor.initialize();
        return executor;
    }
}