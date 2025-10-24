package com.ldsilver.chingoohaja.dto.call.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.CallRecording;
import com.ldsilver.chingoohaja.domain.call.enums.RecordingStatus;

import java.time.LocalDateTime;

public record RecordingResponse(
        @JsonProperty("resource_id") String resourceId,
        @JsonProperty("sid") String sid,
        @JsonProperty("call_id") Long callId,
        @JsonProperty("channel_name") String channelName,
        @JsonProperty("recording_status") RecordingStatus recordingStatus,
        @JsonProperty("file_url") String fileUrl,
        @JsonProperty("file_size") Long fileSize,
        @JsonProperty("started_at") LocalDateTime startedAt,
        @JsonProperty("ended_at") LocalDateTime endedAt,
        @JsonProperty("duration_seconds") Integer durationSeconds
) {
    public static RecordingResponse started(String resourceId, String sid, Long callId, String channelName) {
        return new RecordingResponse(
                resourceId,
                sid,
                callId,
                channelName,
                RecordingStatus.PROCESSING,
                null,
                null,
                LocalDateTime.now(),
                null,
                null
        );
    }

    public static RecordingResponse stopped(String resourceId, String sid, Long callId,
                                            String channelName, String fileUrl, Long fileSize,
                                            LocalDateTime startedAt, Integer durationSeconds) {
        return new RecordingResponse(
                resourceId,
                sid,
                callId,
                channelName,
                RecordingStatus.COMPLETED,
                fileUrl,
                fileSize,
                startedAt,
                LocalDateTime.now(),
                durationSeconds
        );
    }

    public static RecordingResponse failed(String resourceId, String sid, Long callId, String channelName) {
        return new RecordingResponse(
                resourceId,
                sid,
                callId,
                channelName,
                RecordingStatus.FAILED,
                null,
                null,
                LocalDateTime.now(),
                LocalDateTime.now(),
                null
        );
    }

    public static RecordingResponse from(CallRecording recording, Call call) {
        return new RecordingResponse(
                recording.getAgoraResourceId(),
                recording.getAgoraSid(),
                call.getId(),
                call.getAgoraChannelName(),
                recording.getRecordingStatus(),
                recording.getFilePath(),
                recording.getFileSize(),
                recording.getRecordingStartedAt(),
                recording.getRecordingEndedAt(),
                recording.getRecordingDurationSeconds()
        );
    }

    public boolean isProcessing() {
        return recordingStatus == RecordingStatus.PROCESSING;
    }

    public boolean isCompleted() {
        return recordingStatus == RecordingStatus.COMPLETED;
    }

    public boolean hasFailed() {
        return recordingStatus == RecordingStatus.FAILED;
    }

}
