package com.ldsilver.chingoohaja.dto.matching;

import com.ldsilver.chingoohaja.domain.category.enums.CategoryType;

public record MatchingCategoryStats(
        Long categoryId,
        String categoryName,
        Long waitingCount,
        CategoryType categoryType
) {
}
