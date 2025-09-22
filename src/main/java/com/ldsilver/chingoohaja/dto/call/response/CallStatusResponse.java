package com.ldsilver.chingoohaja.dto.call.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.enums.CallStatus;
import com.ldsilver.chingoohaja.domain.call.enums.CallType;
import com.ldsilver.chingoohaja.domain.user.User;

import java.time.LocalDateTime;

public record CallStatusResponse(
        @JsonProperty("call_id") Long callId,
        @JsonProperty("call_status") CallStatus callStatus,
        @JsonProperty("call_type") CallType callType,
        @JsonProperty("category_name") String categoryName,
        @JsonProperty("partner_nickname") String partnerNickname,
        @JsonProperty("partner_id") Long partnerId,
        @JsonProperty("agora_channel_name") String agoraChannelName,
        @JsonProperty("start_at") LocalDateTime startAt,
        @JsonProperty("end_at") LocalDateTime endAt,
        @JsonProperty("duration_seconds") Integer durationSeconds,
        @JsonProperty("is_recording_active") boolean isRecordingActive,
        @JsonProperty("recording_started_at") LocalDateTime recordingStartedAt,
        @JsonProperty("recording_ended_at") LocalDateTime recordingEndedAt,
        @JsonProperty("recording_duration_seconds") Integer recordingDurationSeconds,
        @JsonProperty("created_at") LocalDateTime createdAt
) {
    public static CallStatusResponse from(Call call, Long currentUserId) {
        User partner = call.getPartner(currentUserId);

        return new CallStatusResponse(
                call.getId(),
                call.getCallStatus(),
                call.getCallType(),
                call.getCategory().getName(),
                partner.getNickname(),
                partner.getId(),
                call.getAgoraChannelName(),
                call.getStartAt(),
                call.getEndAt(),
                call.getDurationSeconds(),
                call.isRecordingActive(),
                call.getRecordingStartedAt(),
                call.getRecordingEndedAt(),
                call.getRecordingDurationSeconds(),
                call.getCreatedAt()
        );
    }

    public boolean isActive() {
        return callStatus == CallStatus.READY || callStatus == CallStatus.IN_PROGRESS;
    }

    public boolean isCompleted() {
        return callStatus == CallStatus.COMPLETED;
    }

    public boolean canEnd() {
        return callStatus == CallStatus.READY || callStatus == CallStatus.IN_PROGRESS;
    }

    public boolean hasRecording() {
        return recordingStartedAt != null;
    }
}
