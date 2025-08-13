package com.ldsilver.chingoohaja.dto.oauth.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.validation.AuthValidationConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(
        @NotBlank(message = AuthValidationConstants.Token.REFRESH_TOKEN_REQUIRED)
        @Size(
                min = AuthValidationConstants.Token.MIN_TOKEN_LENGTH,
                max = AuthValidationConstants.Token.MAX_TOKEN_LENGTH,
                message = AuthValidationConstants.Token.REFRESH_TOKEN_INVALID_LENGTH
        )
        @JsonProperty("refresh_token") String refreshToken
) {
    public RefreshTokenRequest {
        if (refreshToken != null && !isValidJwtFormat(refreshToken)) {
            throw new IllegalArgumentException(AuthValidationConstants.Token.REFRESH_TOKEN_INVALID_FORMAT);
        }
    }

    private static boolean isValidJwtFormat(String token) {
        if (token == null) return false;
        String[] parts = token.split("\\.");
        return parts.length == 3;
    }
}
