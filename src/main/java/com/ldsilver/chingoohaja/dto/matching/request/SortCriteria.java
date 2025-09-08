package com.ldsilver.chingoohaja.dto.matching.request;

import com.ldsilver.chingoohaja.domain.matching.enums.SortBy;
import com.ldsilver.chingoohaja.domain.matching.enums.SortOrder;

public record SortCriteria(SortBy sortBy, SortOrder sortOrder) {
    public boolean isAscending() {
        return sortOrder == SortOrder.ASC;
    }

    public boolean isDescending() {
        return sortOrder == SortOrder.DESC;
    }
}
