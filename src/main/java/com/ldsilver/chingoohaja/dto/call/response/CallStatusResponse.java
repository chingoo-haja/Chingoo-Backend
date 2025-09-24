package com.ldsilver.chingoohaja.dto.call.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.enums.CallStatus;
import com.ldsilver.chingoohaja.domain.call.enums.CallType;
import com.ldsilver.chingoohaja.domain.user.User;

import java.time.LocalDateTime;

import static java.util.Objects.requireNonNull;

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
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("can_evaluate") Boolean canEvaluate,
        @JsonProperty("has_evaluated") Boolean hasEvaluated
) {
    public static CallStatusResponse from(Call call, Long currentUserId) {
        requireNonNull(call, "call must not be null");
        final User partner = requireNonNull(
                call.getPartner(currentUserId), "Partner not found for call " + call.getId());

        final var category = call.getCategory();
        final String categoryName = (category != null ? category.getName() : null);

        final var status = call.getCallStatus();
        final String channelName =
                (status == CallStatus.READY || status == CallStatus.IN_PROGRESS)
                        ? call.getAgoraChannelName()
                        : null;

        return new CallStatusResponse(
                call.getId(),
                status,
                call.getCallType(),
                categoryName,
                partner.getNickname(),
                partner.getId(),
                channelName,
                call.getStartAt(),
                call.getEndAt(),
                call.getDurationSeconds(),
                call.isRecordingActive(),
                call.getRecordingStartedAt(),
                call.getRecordingEndedAt(),
                call.getRecordingDurationSeconds(),
                call.getCreatedAt(),
                null,
                null
        );
    }

    public CallStatusResponse withEvaluationInfo(boolean canEvaluate, boolean hasEvaluated) {
        return new CallStatusResponse(
                callId, callStatus, callType, categoryName, partnerNickname, partnerId,
                agoraChannelName, startAt, endAt, durationSeconds, isRecordingActive,
                recordingStartedAt, recordingEndedAt, recordingDurationSeconds, createdAt,
                canEvaluate, hasEvaluated
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
