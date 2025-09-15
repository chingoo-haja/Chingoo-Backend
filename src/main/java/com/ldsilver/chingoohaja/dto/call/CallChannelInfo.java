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
                Set.copyOf(participantIds).size(),
                Set.copyOf(participantIds),
                createdAt,
                expiresAt,
                true
        );

    }

    public static CallChannelInfo empty(String channelName, Long callId) {
        LocalDateTime now = LocalDateTime.now();
        return new CallChannelInfo(
                channelName,
                callId,
                2, // 기본 2명 제한
                0,
                Set.of(),
                now,
                now.plusHours(1), // 기본 1시간 만료
                true
        );
    }


    public boolean isFull() {
        return currentParticipants >= maxParticipants;
    }

    public boolean isEmpty() {
        return currentParticipants == 0;
    }

    public boolean hasParticipant(Long userId) {
        return participantIds.contains(userId);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public CallChannelInfo addParticipant(Long userId) {
        if (isFull()) {
            throw new IllegalStateException("채널이 가득 찼습니다: " + channelName);
        }
        if (hasParticipant(userId)) {
            return this; // 이미 참가 중
        }

        Set<Long> newParticipants = Set.copyOf(participantIds);
        newParticipants.add(userId);

        return new CallChannelInfo(
                channelName,
                callId,
                maxParticipants,
                newParticipants.size(),
                newParticipants,
                createdAt,
                expiresAt,
                isActive
        );
    }

    public CallChannelInfo removeParticipant(Long userId) {
        if (!hasParticipant(userId)) {
            return this; // 참가하지 않은 사용자
        }

        Set<Long> newParticipants = Set.copyOf(participantIds);
        newParticipants.remove(userId);

        return new CallChannelInfo(
                channelName,
                callId,
                maxParticipants,
                newParticipants.size(),
                newParticipants,
                createdAt,
                expiresAt,
                isActive
        );
    }

    public CallChannelInfo deactivate() {
        return new CallChannelInfo(
                channelName,
                callId,
                maxParticipants,
                currentParticipants,
                participantIds,
                createdAt,
                expiresAt,
                false
        );
    }

    public CallChannelInfo extendExpiration(LocalDateTime newExpiresAt) {
        return new CallChannelInfo(
                channelName,
                callId,
                maxParticipants,
                currentParticipants,
                participantIds,
                createdAt,
                newExpiresAt,
                isActive
        );
    }
}
