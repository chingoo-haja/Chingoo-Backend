package com.ldsilver.chingoohaja.dto.matching.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "매칭 큐 헬스 체크 응답")
public record MatchingQueueHealthResponse(
        @Schema(description = "카테고리 정보")
        CategoryInfo category,

        @Schema(description = "Redis 실시간 상태")
        RedisQueueStatus redisStatus,

        @Schema(description = "DB 대기열 상태")
        DatabaseQueueStatus databaseStatus,

        @Schema(description = "시스템 상태", example = "HEALTHY")
        HealthStatus healthStatus,

        @Schema(description = "경고 메시지 (있을 경우)")
        List<String> warnings,

        @Schema(description = "점검 시각")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime timestamp
) {
    @Schema(description = "카테고리 기본 정보")
    public record CategoryInfo(
            Long id,
            String name,
            boolean active
    ) {}

    @Schema(description = "Redis 대기열 상태")
    public record RedisQueueStatus(
            @Schema(description = "현재 대기 중인 사용자 수")
            long waitingCount,

            @Schema(description = "Redis 서버 연결 상태")
            boolean available
    ) {}

    @Schema(description = "DB 대기열 상태 (일관성 체크용)")
    public record DatabaseQueueStatus(
            @Schema(description = "DB WAITING 상태 레코드 수")
            long waitingCount,

            @Schema(description = "DB EXPIRED 상태 레코드 수 (정리 필요)")
            long expiredCount,

            @Schema(description = "Redis-DB 일치 여부")
            boolean consistentWithRedis,

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
}