package com.ldsilver.chingoohaja.dto.call.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.dto.call.CallChannelInfo;

import java.time.LocalDateTime;
import java.util.Set;

public record ChannelResponse(
        @JsonProperty("channel_name") String channelName,
        @JsonProperty("call_id") Long callId,
        @JsonProperty("max_participants") int maxParticipants,
        @JsonProperty("current_participants") int currentParticipants,
        @JsonProperty("participant_ids") Set<Long> participantIds,
        @JsonProperty("is_full") boolean isFull,
        @JsonProperty("is_empty") boolean isEmpty,
        @JsonProperty("is_active") boolean isActive,
        @JsonProperty("expires_at") LocalDateTime expiresAt,
        @JsonProperty("created_at") LocalDateTime createdAt

) {
    public static ChannelResponse from(CallChannelInfo channelInfo) {
        return new ChannelResponse(
                channelInfo.channelName(),
                channelInfo.callId(),
                channelInfo.maxParticipants(),
                channelInfo.currentParticipants(),
                channelInfo.participantIds(),
                channelInfo.isFull(),
                channelInfo.isEmpty(),
                channelInfo.isActive(),
                channelInfo.expiresAt(),
                channelInfo.createdAt()
        );
    }
}
