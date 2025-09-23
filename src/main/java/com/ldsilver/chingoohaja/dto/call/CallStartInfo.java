package com.ldsilver.chingoohaja.dto.call;

public record CallStartInfo(
        Long callId,
        Long partnerId,
        String partnerNickname,
        String channelName,
        String rtcToken,
        Long agoraUid,
        java.time.LocalDateTime expiresAt
) {
}
