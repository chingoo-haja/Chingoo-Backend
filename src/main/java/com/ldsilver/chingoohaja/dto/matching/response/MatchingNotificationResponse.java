package com.ldsilver.chingoohaja.dto.matching.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record MatchingNotificationResponse(
        @JsonProperty("type") String type,
        @JsonProperty("message") String message,
        @JsonProperty("call_id") Long callId,
        @JsonProperty("partner_id") Long partnerId,
        @JsonProperty("partner_nickname") String partnerNickname,
        @JsonProperty("queue_position") Integer queuePosition,
        @JsonProperty("estimated_wait_time") Integer estimatedWaitTime,
        @JsonProperty("timestamp") LocalDateTime timestamp
) {
    public static MatchingNotificationResponse success(Long callId, Long partnerId, String partnerNickname) {
        return new MatchingNotificationResponse(
                "MATCHING_SUCCESS",
                "매칭이 성공했습니다!",
                callId,
                partnerId,
                partnerNickname,
                null,
                null,
                LocalDateTime.now()
        );
    }

    public static MatchingNotificationResponse cancelled(String reason) {
        return new MatchingNotificationResponse(
                "MATCHING_CANCELLED",
                "매칭이 취소되었습니다: " + reason,
                null,
                null,
                null,
                null,
                null,
                LocalDateTime.now()
        );
    }

    public static MatchingNotificationResponse queueUpdate(Integer position, Integer estimatedWaitTime) {
        return new MatchingNotificationResponse(
                "QUEUE_UPDATE",
                "대기열 위치가 업데이트되었습니다.",
                null,
                null,
                null,
                position,
                estimatedWaitTime,
                LocalDateTime.now()
        );
    }

    public static MatchingNotificationResponse expired() {
        return new MatchingNotificationResponse(
                "MATCHING_EXPIRED",
                "매칭 대기 시간이 만료되었습니다.",
                null,
                null,
                null,
                null,
                null,
                LocalDateTime.now()
        );
    }
}
