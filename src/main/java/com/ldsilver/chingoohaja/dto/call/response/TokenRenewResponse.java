package com.ldsilver.chingoohaja.dto.call.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "RTC Token 갱신 응답")
public record TokenRenewResponse(
        @Schema(description = "새로운 RTC Token", example = "006abcdef...")
        String rtcToken,

        @Schema(description = "토큰 만료 시각", example = "2025-10-15T12:00:00")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime expiresAt
) {
    public static TokenRenewResponse of(String rtcToken, LocalDateTime expiresAt) {
        return new TokenRenewResponse(rtcToken, expiresAt);
    }
}
