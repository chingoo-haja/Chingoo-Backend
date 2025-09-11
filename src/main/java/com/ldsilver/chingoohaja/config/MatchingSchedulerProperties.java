package com.ldsilver.chingoohaja.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.matching.scheduler")
public class MatchingSchedulerProperties {
    /**
     * 스케줄러 활성화 여부
     */
    private boolean enabled = true;

    /**
     * 매칭 처리 주기 (밀리초)
     * 기본값: 30초
     */
    @Min(value = 1000, message = "매칭 처리 주기는 최소 1초 이상이어야 합니다.")
    private long matchingDelay = 30000L;

    /**
     * 만료된 큐 정리 주기 (밀리초)
     * 기본값: 5분
     */
    @Min(value = 10000, message = "정리 주기는 최소 10초 이상이어야 합니다.")
    private long cleanupDelay = 300000L;

    /**
     * 큐 만료 시간 (초)
     * 기본값: 10분
     */
    @Min(value = 60, message = "만료 시간은 최소 1분 이상이어야 합니다.")
    private int expiredTime = 600;

    /**
     * 로그 레벨 (local 환경에서 로그 줄이기)
     */
    private LogLevel logLevel = LogLevel.INFO;

    public enum LogLevel {
        DEBUG, INFO, WARN
    }

    public boolean isDebugLogEnabled() {
        return logLevel == LogLevel.DEBUG;
    }
}
