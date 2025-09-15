package com.ldsilver.chingoohaja.dto.call;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Set;

public record CallChannelInfo(
        @JsonProperty("channel_name") String channelName,
        @JsonProperty("call_id") Long callId,
        @JsonProperty("max_participants") int maxParticipants,
        @JsonProperty("current_participants") int currentParticipants,
        @JsonProperty("participant_ids")Set<Long> participantIds,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("expires_at") LocalDateTime expiresAt,
        @JsonProperty("is_active") boolean isActive)
{
    public static CallChannelInfo of(
            String channelName,
            Long callId,
            int maxParticipants,
            Set<Long> participantIds,
            LocalDateTime createdAt,
            LocalDateTime expiresAt) {
        return new CallChannelInfo(
                channelName,
                callId,
                maxParticipants,
                participantIds.size(),
                participantIds,
                createdAt,
                expiresAt,
                true
        );

    }
}
