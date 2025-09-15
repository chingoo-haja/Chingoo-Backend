package com.ldsilver.chingoohaja.dto.call.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record TokenResponse(
        @JsonProperty("rtc_token") String rtcToken,
        @JsonProperty("rtm_token") String rtmToken,
        @JsonProperty("channel_name") String channelName,
        @JsonProperty("agora_uid") Long agoraUid,
        @JsonProperty("user_id") Long userId,
        @JsonProperty("role") String role,
        @JsonProperty("expires_at") LocalDateTime expiresAt,
        @JsonProperty("issued_at") LocalDateTime issuedAt
) {
    public static TokenResponse of(
            String rtcToken,
            String rtmToken,
            String channelName,
            Long agoraUid,
            Long userId,
            String role,
            LocalDateTime expiresAt
    ) {
        return new TokenResponse(
                rtcToken,
                rtmToken,
                channelName,
                agoraUid,
                userId,
                role,
                expiresAt,
                LocalDateTime.now()
        );
    }

    public static TokenResponse rtcOnly(
            String rtcToken,
            String channelName,
            Long agoraUid,
            Long userId,
            String role,
            LocalDateTime expiresAt
    ) {
        return new TokenResponse(
                rtcToken,
                null,
                channelName,
                agoraUid,
                userId,
                role,
                expiresAt,
                LocalDateTime.now()
        );
    }

    // Agora UID 관련 헬퍼 메서드들
    public boolean isValidAgoraUid() {
        return agoraUid != null && agoraUid >= 0 && agoraUid <= 4_294_967_295L;
    }

    public String getFormattedAgoraUid() {
        return agoraUid != null ? String.format("UID:%d", agoraUid) : "UID:AUTO";
    }

    // 기존 메서드들
    public boolean hasRtmToken() {
        return rtmToken != null && !rtmToken.trim().isEmpty();
    }

    public String maskedRtcToken() {
        return maskToken(rtcToken);
    }

    public String maskedRtmToken() {
        return maskToken(rtmToken);
    }

    private String maskToken(String token) {
        if (token == null) {
            return "<null>";
        }
        if (token.length() < 8) {
            return "<short>";
        }
        return token.substring(0, 4) + "***" + token.substring(token.length() - 4);
    }

    @Override
    public String toString() {
        return "TokenResponse{" +
                "rtcToken='" + maskedRtcToken() + '\'' +
                ", rtmToken='" + maskedRtmToken() + '\'' +
                ", channelName='" + channelName + '\'' +
                ", agoraUid=" + agoraUid +
                ", userId=" + userId +
                ", role='" + role + '\'' +
                ", expiresAt=" + expiresAt +
                ", issuedAt=" + issuedAt +
                '}';
    }
}
