package com.ldsilver.chingoohaja.dto.friendship.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.domain.friendship.Friendship;

import java.time.LocalDateTime;
import java.util.List;

public record PendingFriendRequestListResponse(
        @JsonProperty("requests") List<PendingFriendRequestItem> requests,
        @JsonProperty("total_count") int totalCount
) {
    public record PendingFriendRequestItem(
            @JsonProperty("friendship_id") Long friendshipId,
            @JsonProperty("requester_id") Long requesterId,
            @JsonProperty("requester_nickname") String requesterNickname,
            @JsonProperty("requester_profile_image_url") String requesterProfileImageUrl,
            @JsonProperty("requested_at") LocalDateTime requestedAt
    ) {
        public static PendingFriendRequestItem from(Friendship friendship) {
            var requester = friendship.getRequester();

            return new PendingFriendRequestItem(
                    friendship.getId(),
                    requester.getId(),
                    requester.getNickname(),
                    requester.getProfileImageUrl(),
                    friendship.getCreatedAt()
            );
        }
    }

    public static PendingFriendRequestListResponse of(List<PendingFriendRequestItem> requests) {
        return new PendingFriendRequestListResponse(requests, requests.size());
    }

    public static PendingFriendRequestListResponse empty() {
        return new PendingFriendRequestListResponse(List.of(), 0);
    }
}
