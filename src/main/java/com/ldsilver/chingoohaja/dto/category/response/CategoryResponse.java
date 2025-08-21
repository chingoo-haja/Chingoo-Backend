package com.ldsilver.chingoohaja.dto.category.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.domain.category.Category;

import java.time.LocalDateTime;

public record CategoryResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("name") String name,
        @JsonProperty("is_active") boolean isActive,
        @JsonProperty("category_type") String categoryType,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("updated_at") LocalDateTime updatedAt
) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.isActive(),
                category.getCategoryType().name(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }

    public boolean isRandomMatchingCategory() {
        return "RANDOM".equals(categoryType);
    }

    public boolean isGuardianCategory() {
        return "GUARDIAN".equals(categoryType);
    }
}
