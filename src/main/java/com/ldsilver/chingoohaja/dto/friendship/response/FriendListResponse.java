package com.ldsilver.chingoohaja.dto.friendship.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.user.User;

import java.time.LocalDateTime;
import java.util.List;

public record FriendListResponse (
        @JsonProperty("friends") List<FriendItem> friends,
        @JsonProperty("total_count") int totalCount
){
    public record FriendItem(
            @JsonProperty("friend_id") Long friendId,
            @JsonProperty("nickname") String nickname,
            @JsonProperty("last_call_at") LocalDateTime lastCallAt,
            @JsonProperty("last_call_category_name") String lastCallCategoryName
    ) {
        public static FriendItem of(User friend, Call lastCall) {
            LocalDateTime lastCallAt = null;
            String categoryName = null;

            if (lastCall != null) {
                lastCallAt = lastCall.getEndAt();

                var category = lastCall.getCategory();
                if (category != null) {
                    categoryName = category.getName();
                }
            }

            return new FriendItem(
                    friend.getId(),
                    friend.getNickname(),
                    lastCallAt,
                    categoryName
            );
        }
    }
    public static FriendListResponse of(List<FriendItem> friends) {
        return new FriendListResponse(friends, friends.size());
    }
}
