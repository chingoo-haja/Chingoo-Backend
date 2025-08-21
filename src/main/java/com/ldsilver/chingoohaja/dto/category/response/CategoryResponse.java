package com.ldsilver.chingoohaja.dto.category.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.domain.category.Category;
import com.ldsilver.chingoohaja.domain.category.enums.CategoryType;

import java.time.LocalDateTime;

public record CategoryResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("name") String name,
        @JsonProperty("is_active") boolean isActive,
        @JsonProperty("category_type") CategoryType categoryType,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("updated_at") LocalDateTime updatedAt
) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.isActive(),
                category.getCategoryType(),
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
