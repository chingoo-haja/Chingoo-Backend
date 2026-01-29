package com.ldsilver.chingoohaja.dto.matching.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "매칭 큐 정리 결과")
public record MatchingQueueCleanupResponse(
        @JsonProperty("category_id")
        @Schema(description = "카테고리 ID", example = "4")
        Long categoryId,

        @JsonProperty("category_name")
        @Schema(description = "카테고리 이름", example = "음악/악기")
        String categoryName,

        @JsonProperty("before")
        @Schema(description = "정리 전 상태")
        BeforeStatus before,

        @JsonProperty("after")
        @Schema(description = "정리 후 상태")
        AfterStatus after,

        @JsonProperty("cleaned")
        @Schema(description = "정리 작업 결과")
        CleanedResult cleaned,

        @JsonProperty("timestamp")
        @Schema(description = "정리 실행 시각")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime timestamp
) {
    @Schema(description = "정리 전 대기열 상태")
    public record BeforeStatus(
            @JsonProperty("redis_waiting")
            @Schema(description = "Redis 대기 인원", example = "1")
            long redisWaiting,

            @JsonProperty("db_waiting")
            @Schema(description = "DB WAITING 상태 레코드 수", example = "0")
            long dbWaiting
    ) {}

    @Schema(description = "정리 후 대기열 상태")
    public record AfterStatus(
            @JsonProperty("redis_waiting")
            @Schema(description = "Redis 대기 인원 (정리 후)", example = "0")
            long redisWaiting,

            @JsonProperty("db_waiting")
            @Schema(description = "DB WAITING 상태 레코드 수 (정리 후)", example = "0")
            long dbWaiting
    ) {}

    @Schema(description = "정리 작업 결과")
    public record CleanedResult(
            @JsonProperty("redis_deleted")
            @Schema(description = "Redis 키 삭제 성공 여부", example = "true")
            boolean redisDeleted,

            @JsonProperty("db_updated_count")
            @Schema(description = "DB에서 EXPIRED로 변경된 레코드 수", example = "1")
            int dbUpdatedCount
    ) {}

    public static MatchingQueueCleanupResponse of(
            Long categoryId,
            String categoryName,
            long redisBeforeCount,
            long dbBeforeCount,
            long redisAfterCount,
            long dbAfterCount,
            boolean redisDeleted,
            int dbUpdatedCount
    ) {
        return new MatchingQueueCleanupResponse(
                categoryId,
                categoryName,
                new BeforeStatus(redisBeforeCount, dbBeforeCount),
                new AfterStatus(redisAfterCount, dbAfterCount),
                new CleanedResult(redisDeleted, dbUpdatedCount),
                LocalDateTime.now()
        );
    }
}