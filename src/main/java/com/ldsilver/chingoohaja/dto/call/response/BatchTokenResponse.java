package com.ldsilver.chingoohaja.dto.call.response;

public record BatchTokenResponse(
        TokenResponse user1Token,
        TokenResponse user2Token
) {
    public boolean bothTokenValid() {
        return user1Token != null && user2Token != null &&
                user1Token.rtcToken() != null && user2Token.rtcToken() != null;
    }

    public String getChannelName() {
        return user1Token != null ? user1Token.channelName() : null;
    }
}
