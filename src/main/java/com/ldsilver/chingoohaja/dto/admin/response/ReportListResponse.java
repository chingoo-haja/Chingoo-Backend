package com.ldsilver.chingoohaja.dto.admin.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

public record ReportListResponse(
        @JsonProperty("reports") List<ReportDetail> reports,
        @JsonProperty("pagination") Pagination pagination
) {
    public record ReportDetail(
            @JsonProperty("report_id") Long reportId,
            @JsonProperty("reporter") UserInfo reporter,
            @JsonProperty("reported_user") UserInfo reportedUser,
            @JsonProperty("call_id") Long callId,
            @JsonProperty("reason") String reason,
            @JsonProperty("details") String details,
            @JsonProperty("created_at") LocalDateTime createdAt,
            @JsonProperty("status") String status // PENDING, REVIEWED, RESOLVED
    ) {}

    public record UserInfo(
            @JsonProperty("user_id") Long userId,
            @JsonProperty("nickname") String nickname,
            @JsonProperty("email") String email
    ) {}

    public record Pagination(
            @JsonProperty("current_page") int currentPage,
            @JsonProperty("total_pages") int totalPages,
            @JsonProperty("total_count") long totalCount
    ) {}
}