package com.ldsilver.chingoohaja.dto.admin.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.enums.CallStatus;

import java.time.LocalDateTime;

public record AdminForceEndCallResponse(
        @JsonProperty("call_id") Long callId,
        @JsonProperty("previous_status") CallStatus previousStatus,
        @JsonProperty("current_status") CallStatus currentStatus,
        @JsonProperty("user1_nickname") String user1Nickname,
        @JsonProperty("user2_nickname") String user2Nickname,
        @JsonProperty("category_name") String categoryName,
        @JsonProperty("start_at") LocalDateTime startAt,
        @JsonProperty("end_at") LocalDateTime endAt,
        @JsonProperty("duration_seconds") Integer durationSeconds,
        @JsonProperty("force_ended_at") LocalDateTime forceEndedAt,
        @JsonProperty("force_ended_by") Long forceEndedBy,
        @JsonProperty("message") String message
) {
    public static AdminForceEndCallResponse of(
            Call call,
            CallStatus previousStatus,
            Long adminId
    ) {
        String categoryName = call.getCategory() != null ? call.getCategory().getName() : null;

        return new AdminForceEndCallResponse(
                call.getId(),
                previousStatus,
                call.getCallStatus(),
                call.getUser1().getNickname(),
                call.getUser2().getNickname(),
                categoryName,
                call.getStartAt(),
                call.getEndAt(),
                call.getDurationSeconds(),
                LocalDateTime.now(),
                adminId,
                "통화가 관리자에 의해 강제 종료되었습니다."
        );
    }
}
