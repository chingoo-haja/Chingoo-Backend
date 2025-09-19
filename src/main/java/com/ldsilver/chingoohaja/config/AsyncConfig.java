package com.ldsilver.chingoohaja.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {
    /**
     * Recording 전용 비동기 TaskExecutor
     */
    @Bean("recordingTaskExecutor")
    public Executor recordingTaskExecutor() {
        ThreadPoolTaskExecutor executor = getThreadPoolTaskExecutor();

        executor.setThreadFactory(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("Recording-Async-" + thread.getId());
            thread.setUncaughtExceptionHandler((t, ex) ->
                    log.error("Recording 비동기 작업 예외 발생 - Thread: {}", t.getName(), ex));
            return thread;
        });
        executor.initialize();

        log.info("Recording TaskExecutor 초기화 완료 - Core: {}, Max: {}, Queue: {}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }

    private static ThreadPoolTaskExecutor getThreadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 기본 스레드 수 (항상 유지)
        executor.setCorePoolSize(2);

        // 최대 스레드 수
        executor.setMaxPoolSize(5);

        // 큐 용량 (대기 작업 수)
        executor.setQueueCapacity(10);

        // 스레드 이름 접두어
        executor.setThreadNamePrefix("Recording-Async-");

        // 거부 정책: 호출자 스레드에서 실행
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 애플리케이션 종료 시 진행 중인 작업 완료까지 대기
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
