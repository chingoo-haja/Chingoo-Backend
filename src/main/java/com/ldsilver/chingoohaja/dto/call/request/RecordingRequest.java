package com.ldsilver.chingoohaja.dto.call.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.validation.CommonValidationConstants;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecordingRequest(
        @NotNull(message = "통화 ID는 필수입니다.")
        @Min(value = CommonValidationConstants.Id.MIN_VALUE, message = CommonValidationConstants.Id.INVALID_ID)
        @JsonProperty("call_id") Long callId,

        @NotBlank(message = "채널명은 필수입니다.")
        @Size(min = 1, max = 64, message = "채널명은 1-64자여야 합니다.")
        @JsonProperty("channel_name") String channelName,

        @JsonProperty("max_idle_time") Integer maxIdleTime,
        @JsonProperty("audio_profile") Integer audioProfile,
        @JsonProperty("enable_audio_only") Boolean enableAudioOnly
        ) {
    public RecordingRequest {
        // 기본값 설정
        if (maxIdleTime == null || maxIdleTime < 10 || maxIdleTime > 300) {
            maxIdleTime = 30; // 30초
        }
        if (audioProfile == null || audioProfile < 0 || audioProfile > 2) {
            audioProfile = 0; // 기본 오디오 프로필
        }
        if (enableAudioOnly == null) {
            enableAudioOnly = true; // 비용 절약을 위해 오디오만
        }
    }

    public static RecordingRequest of(Long callId, String channelName) {
        return new RecordingRequest(callId, channelName, 30, 0, true);
    }

    public static RecordingRequest withCustomConfig(Long callId, String channelName, int maxIdleTime, int audioProfile, boolean enableAudioOnly) {
        return new RecordingRequest(callId, channelName, maxIdleTime, audioProfile, enableAudioOnly);
    }

    public int getChannelType() {
        return 0; // 0: 통신 모드, 1: 라이브 방송 모드
    }

    public int getStreamTypes() {
        return enableAudioOnly ? 0 : 2; // 0: 오디오만, 1: 비디오만, 2: 오디오+비디오
    }

    public boolean shouldSubscribeAudioOnly() {
        return enableAudioOnly;
    }
}
