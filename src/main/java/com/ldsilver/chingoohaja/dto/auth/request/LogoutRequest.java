package com.ldsilver.chingoohaja.dto.auth.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.validation.AuthValidationConstants;
import jakarta.validation.constraints.Size;

public record LogoutRequest(
        @Size(
                min = AuthValidationConstants.Token.MIN_TOKEN_LENGTH,
                max = AuthValidationConstants.Token.MAX_TOKEN_LENGTH,
                message = AuthValidationConstants.Token.REFRESH_TOKEN_INVALID_LENGTH
        )
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("logout_all") Boolean logoutAll
) {
    public LogoutRequest{
        logoutAll = logoutAll != null ? logoutAll : false;
    }

    public boolean isLogoutAll() {
        return Boolean.TRUE.equals(logoutAll);
    }

    public boolean hasRefreshToken() {
        return refreshToken != null && !refreshToken.trim().isEmpty();
    }
}
