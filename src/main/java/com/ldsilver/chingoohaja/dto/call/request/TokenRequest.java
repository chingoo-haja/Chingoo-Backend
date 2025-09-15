package com.ldsilver.chingoohaja.dto.call.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.validation.CallValidationConstants;
import com.ldsilver.chingoohaja.validation.CommonValidationConstants;
import jakarta.validation.constraints.*;

public record TokenRequest(
        @NotBlank(message = "채널명은 필수입니다.")
        @Size(min = 1, max = 64, message = "채널명은 1-64자여야 합니다.")
        @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "채널명은 영문, 숫자, 언더스코어, 하이픈만 사용 가능합니다.")
        @JsonProperty("channel_name") String channelName,

        @NotNull(message = "사용자 ID는 필수입니다.")
        @Min(value = CommonValidationConstants.Id.MIN_VALUE, message = CommonValidationConstants.Id.INVALID_ID)
        @JsonProperty("user_id") Long userId,

        @Min(value = 0, message = "Agora UID는 0 이상이어야 합니다.")
        @JsonProperty("agora_uid") Long agoraUid,

        @Min(value = 60, message = "만료 시간은 최소 60초 이상이어야 합니다.")
        @Max(value = 86_400, message = "만료 시간은 최대 24시간(86,400초) 이하로 설정해야 합니다.")
        @JsonProperty("expiration_seconds") Integer expirationSeconds,

        @Pattern(regexp = "^(PUBLISHER|SUBSCRIBER)$", message = "역할은 PUBLISHER 또는 SUBSCRIBER여야 합니다.")
        @JsonProperty("role") String role,

        @JsonProperty("include_rtm_token") Boolean includeRtmToken
) {

    public TokenRequest {
        // 기본값 설정
        agoraUid = agoraUid != null ? agoraUid : 0L; // 0이면 Agora가 자동 할당
        expirationSeconds = expirationSeconds != null ? expirationSeconds : CallValidationConstants.DEFAULT_TTL_SECONDS; // 기본 1시간
        role = role != null ? role : CallValidationConstants.DEFAULT_ROLE;
        includeRtmToken = includeRtmToken != null ? includeRtmToken : false;
    }

    public static TokenRequest of(String channelName, Long userId) {
        return new TokenRequest(channelName, userId, 0L, CallValidationConstants.DEFAULT_TTL_SECONDS, CallValidationConstants.DEFAULT_ROLE, false);
    }

    public static TokenRequest withRole(String channelName, Long userId, String role) {
        return new TokenRequest(channelName, userId, 0L, CallValidationConstants.DEFAULT_TTL_SECONDS, role, false);
    }

    public static TokenRequest withRtm(String channelName, Long userId) {
        return new TokenRequest(channelName, userId, 0L, CallValidationConstants.DEFAULT_TTL_SECONDS, CallValidationConstants.DEFAULT_ROLE, true);
    }

    // Agora UID 관련 헬퍼 메서드들
    public boolean hasCustomAgoraUid() {
        return agoraUid != null && agoraUid > 0;
    }

    public Long getEffectiveAgoraUid() {
        return hasCustomAgoraUid() ? agoraUid : userId;
    }

    public boolean isValidAgoraUidRange() {
        final long AGORA_MAX_UID = 4_294_967_295L; // 32-bit unsigned max
        return agoraUid >= 0 && agoraUid <= AGORA_MAX_UID;
    }

    public boolean isPublisher() {
        return CallValidationConstants.DEFAULT_ROLE.equals(role);
    }

    public boolean needsRtmToken() {
        return Boolean.TRUE.equals(includeRtmToken);
    }

    public int getAgoraRole() {
        return isPublisher() ? 1 : 2; // Agora: 1=Publisher, 2=Subscriber
    }
}
