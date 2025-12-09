package com.ldsilver.chingoohaja.dto.friendship.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.domain.friendship.Friendship;

import java.time.LocalDateTime;
import java.util.List;

public record SentFriendRequestListResponse(
        @JsonProperty("requests") List<SentFriendRequestItem> requests,
        @JsonProperty("total_count") int totalCount
) {
    public record SentFriendRequestItem(
            @JsonProperty("friendship_id") Long friendshipId,
            @JsonProperty("addressee_id") Long addresseeId,
            @JsonProperty("addressee_nickname") String addresseeNickname,
            @JsonProperty("addressee_profile_image_url") String addresseeProfileImageUrl,
            @JsonProperty("requested_at") LocalDateTime requestedAt
    ) {
        public static SentFriendRequestItem from(Friendship friendship) {
            var addressee = friendship.getAddressee();

            return new SentFriendRequestItem(
                    friendship.getId(),
                    addressee.getId(),
                    addressee.getNickname(),
                    addressee.getProfileImageUrl(),
                    friendship.getCreatedAt()
            );
        }
    }

    public static SentFriendRequestListResponse of(List<SentFriendRequestItem> requests) {
        return new SentFriendRequestListResponse(requests, requests.size());
    }

    public static SentFriendRequestListResponse empty() {
        return new SentFriendRequestListResponse(List.of(), 0);
    }
}