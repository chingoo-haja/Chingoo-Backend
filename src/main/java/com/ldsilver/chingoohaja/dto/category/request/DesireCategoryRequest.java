package com.ldsilver.chingoohaja.dto.category.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DesireCategoryRequest(
        @NotBlank(message = "카테고리 이름은 필수입니다.")
        @Size(min = 1, max = 100, message = "카테고리 이름은 1~100자 사이여야 합니다.")
        @JsonProperty("category_name")
        String categoryName
) {
    public DesireCategoryRequest {
        if (categoryName != null) {
            categoryName = categoryName.trim();
        }
    }
}
