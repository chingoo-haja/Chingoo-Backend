package com.ldsilver.chingoohaja.dto.matching.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "매칭 큐 헬스 체크 응답")
public record MatchingQueueHealthResponse(
        @JsonProperty("category")
        @Schema(description = "카테고리 정보")
        CategoryInfo category,

        @JsonProperty("redis_status")
        @Schema(description = "Redis 실시간 상태")
        RedisQueueStatus redisStatus,

        @JsonProperty("database_status")
        @Schema(description = "DB 대기열 상태")
        DatabaseQueueStatus databaseStatus,

        @JsonProperty("health_status")
        @Schema(description = "시스템 상태", example = "HEALTHY")
        HealthStatus healthStatus,

        @JsonProperty("warnings")
        @Schema(description = "경고 메시지 (있을 경우)")
        List<String> warnings,

        @JsonProperty("timestamp")
        @Schema(description = "점검 시각")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime timestamp
) {
    @Schema(description = "카테고리 기본 정보")
    public record CategoryInfo(
            @JsonProperty("id")
            Long id,

            @JsonProperty("name")
            String name,

            @JsonProperty("active")
            boolean active
    ) {}

    @Schema(description = "Redis 대기열 상태")
    public record RedisQueueStatus(
            @JsonProperty("waiting_count")
            @Schema(description = "현재 대기 중인 사용자 수")
            long waitingCount,

            @JsonProperty("available")
            @Schema(description = "Redis 서버 연결 상태")
            boolean available
    ) {}

    @Schema(description = "DB 대기열 상태 (일관성 체크용)")
    public record DatabaseQueueStatus(
            @JsonProperty("waiting_count")
            @Schema(description = "DB WAITING 상태 레코드 수")
            long waitingCount,

            @JsonProperty("expired_count")
            @Schema(description = "DB EXPIRED 상태 레코드 수 (정리 필요)")
            long expiredCount,

            @JsonProperty("consistent_with_redis")
            @Schema(description = "Redis-DB 일치 여부")
            boolean consistentWithRedis,

            @JsonProperty("gap")
            @Schema(description = "불일치 갭 (절대값)")
            long gap
    ) {}

    public enum HealthStatus {
        @Schema(description = "정상")
        HEALTHY,
        @Schema(description = "주의")
        WARNING,
        @Schema(description = "심각")
        CRITICAL
    }

    public static MatchingQueueHealthResponse of(
            Long categoryId,
            String categoryName,
            boolean categoryActive,
            long redisWaiting,
            boolean redisAvailable,
            long dbWaiting,
            long dbExpired,
            boolean consistent,
            long gap,
            HealthStatus healthStatus,
            List<String> warnings
    ) {
        return new MatchingQueueHealthResponse(
                new CategoryInfo(categoryId, categoryName, categoryActive),
                new RedisQueueStatus(redisWaiting, redisAvailable),
                new DatabaseQueueStatus(dbWaiting, dbExpired, consistent, gap),
                healthStatus,
                warnings,
                LocalDateTime.now()
        );
    }
}