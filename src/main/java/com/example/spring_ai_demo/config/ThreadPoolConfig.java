package com.example.spring_ai_demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class ThreadPoolConfig {

    /**
     * 向量化处理线程池 - 控制并发数避免API限流
     */
    @Bean("vectorProcessExecutor")
    public Executor vectorProcessExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数：同时处理的批次数
        executor.setCorePoolSize(3);
        
        // 最大线程数：峰值时的处理能力
        executor.setMaxPoolSize(5);
        
        // 队列容量：等待处理的任务数
        executor.setQueueCapacity(20);
        
        // 线程名前缀
        executor.setThreadNamePrefix("VectorProcess-");
        
        // 拒绝策略：调用者运行，避免任务丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 线程空闲时间
        executor.setKeepAliveSeconds(60);
        
        // 允许核心线程超时
        executor.setAllowCoreThreadTimeOut(true);
        
        executor.initialize();
        return executor;
    }

    /**
     * 数据获取线程池 - 用于并发获取分页数据
     */
    @Bean("dataFetchExecutor")
    public Executor dataFetchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 数据获取可以有更高的并发
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("DataFetch-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);
        
        executor.initialize();
        return executor;
    }
}