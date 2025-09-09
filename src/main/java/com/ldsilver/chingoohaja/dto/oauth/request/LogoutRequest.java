package com.ldsilver.chingoohaja.dto.oauth.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LogoutRequest(
        @JsonProperty("logout_all") Boolean logoutAll
) {
    public LogoutRequest{
        logoutAll = logoutAll != null ? logoutAll : false;
    }

    public boolean isLogoutAll() {
        return Boolean.TRUE.equals(logoutAll);
    }
}
