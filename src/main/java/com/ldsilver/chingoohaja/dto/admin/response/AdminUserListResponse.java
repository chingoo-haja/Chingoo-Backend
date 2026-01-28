package com.ldsilver.chingoohaja.dto.admin.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

public record AdminUserListResponse(
        @JsonProperty("users") List<UserSummary> users,
        @JsonProperty("pagination") Pagination pagination
) {
    public record UserSummary(
            @JsonProperty("user_id") Long userId,
            @JsonProperty("email") String email,
            @JsonProperty("nickname") String nickname,
            @JsonProperty("user_type") String userType,
            @JsonProperty("provider") String provider,
            @JsonProperty("created_at") LocalDateTime createdAt,
            @JsonProperty("last_login") LocalDateTime lastLogin,
            @JsonProperty("total_calls") int totalCalls,
            @JsonProperty("report_count") int reportCount,
            @JsonProperty("is_suspended") boolean isSuspended
    ) {}

    public record Pagination(
            @JsonProperty("current_page") int currentPage,
            @JsonProperty("total_pages") int totalPages,
            @JsonProperty("total_count") long totalCount,
            @JsonProperty("has_next") boolean hasNext
    ) {}
}