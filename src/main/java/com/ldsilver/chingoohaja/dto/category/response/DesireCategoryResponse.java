package com.ldsilver.chingoohaja.dto.category.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.domain.category.DesiredCategory;

import java.time.LocalDateTime;

public record DesireCategoryResponse(
        @JsonProperty("request_id") Long requestId,
        @JsonProperty("category_name") String categoryName,
        @JsonProperty("status") String status,
        @JsonProperty("requested_at") LocalDateTime requestedAt
) {
    public static DesireCategoryResponse from(DesiredCategory request) {
        return new DesireCategoryResponse(
                request.getId(),
                request.getCategoryName(),
                request.getStatus().name(),
                request.getCreatedAt()
        );
    }
}
