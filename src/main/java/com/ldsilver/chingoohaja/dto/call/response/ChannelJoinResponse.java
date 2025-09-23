package com.ldsilver.chingoohaja.dto.call.response;

public record ChannelJoinResponse(
        ChannelResponse channel,
        TokenResponse token,
        String partnerNickname
) {
}
