package com.ldsilver.chingoohaja.dto.auth.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.infrastructure.jwt.JwtTokenProvider;

import java.time.LocalDateTime;

public record TokenValidationResponse(
        @JsonProperty("is_valid") boolean isValid,
        @JsonProperty("user_id") Long userId,
        @JsonProperty("email") String email,
        @JsonProperty("user_type") String userType,
        @JsonProperty("expires_at")LocalDateTime expiresAt
        ) {

    public static TokenValidationResponse fromValidToken(JwtTokenProvider jwtTokenProvider, String accessToken) {
        return new TokenValidationResponse(
                true,
                jwtTokenProvider.getUserIdFromToken(accessToken),
                jwtTokenProvider.getEmailFromToken(accessToken),
                jwtTokenProvider.getUserTypeFromToken(accessToken),
                jwtTokenProvider.getExpirationFromToken(accessToken)
        );
    }

    public static TokenValidationResponse invalid() {
        return new TokenValidationResponse(
                false,
                null,
                null,
                null,
                null
        );
    }

    public boolean isExpired() {
        if (!isValid || expiresAt == null) {
            return true;
        }
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public long getSecondsUntilExpiration() {
        if (!isValid || expiresAt == null) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(expiresAt)) {
            return 0;
        }

        return java.time.Duration.between(now, expiresAt).getSeconds();
    }


}
