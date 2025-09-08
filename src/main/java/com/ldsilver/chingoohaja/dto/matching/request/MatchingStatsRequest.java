package com.ldsilver.chingoohaja.dto.matching.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.domain.matching.enums.PeriodType;
import com.ldsilver.chingoohaja.domain.matching.enums.SortBy;
import com.ldsilver.chingoohaja.domain.matching.enums.SortOrder;
import com.ldsilver.chingoohaja.validation.CommonValidationConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record MatchingStatsRequest(
        @Pattern(
                regexp = "^(DAILY|WEEKLY|MONTHLY|HOURLY|REALTIME)$",
                message = "기간 타입은 DAILY, WEEKLY, MONTHLY, HOURLY, REALTIME 중 하나여야 합니다."
        )
        @JsonProperty("period")
        String period,

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = CommonValidationConstants.Date.DATE_PATTERN)
        @JsonProperty("start_date")
        LocalDate startDate,

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = CommonValidationConstants.Date.DATE_PATTERN)
        @JsonProperty("end_date")
        LocalDate endDate,

        @Min(value = 1, message = "카테고리 ID는 1 이상이어야 합니다.")
        @JsonProperty("category_id")
        Long categoryId,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
        @JsonProperty("limit")
        Integer limit,

        @Min(value = 0, message = "오프셋은 0 이상이어야 합니다.")
        @JsonProperty("offset")
        Integer offset,

        @Pattern(
                regexp = "^(SUCCESS_RATE|WAIT_TIME|POPULARITY|RECENT)$",
                message = "정렬 기준은 SUCCESS_RATE, WAIT_TIME, POPULARITY, RECENT 중 하나여야 합니다."
        )
        @JsonProperty("sort_by")
        String sortBy,

        @Pattern(
                regexp = "^(ASC|DESC)$",
                message = "정렬 순서는 ASC 또는 DESC여야 합니다."
        )
        @JsonProperty("sort_order")
        String sortOrder,

        @JsonProperty("include_inactive")
        Boolean includeInactive,

        @JsonProperty("include_trends")
        Boolean includeTrends,

        @JsonProperty("timezone")
        String timezone
) {
    public MatchingStatsRequest {
        period = period != null ? period : "DAILY";
        limit = limit != null ? limit : 10;
        offset = offset != null ? offset : 0;
        sortBy = sortBy != null ? sortBy : "RECENT";
        sortOrder = sortOrder != null ? sortOrder : "DESC";
        includeInactive = includeInactive != null ? includeInactive : false;
        includeTrends = includeTrends != null ? includeTrends : true;
        timezone = timezone != null ? timezone : "Asia/Seoul";

        // 📝 비즈니스 룰 검증
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("종료 날짜는 시작 날짜보다 이후여야 합니다.");
        }

        if (startDate != null && endDate != null && startDate.plusYears(1).isBefore(endDate)) {
            throw new IllegalArgumentException("날짜 범위는 1년을 초과할 수 없습니다.");
        }

        if ("REALTIME".equals(period) && (startDate != null || endDate != null)) {
            throw new IllegalArgumentException("실시간 조회는 날짜 범위를 지정할 수 없습니다.");
        }
    }

    public static MatchingStatsRequest daily() {
        return new MatchingStatsRequest(
                "DAILY", LocalDate.now().minusDays(7), LocalDate.now(),
                null, 10, 0, "RECENT", "DESC", false, true, "Asia/Seoul"
        );
    }

    public static MatchingStatsRequest weekly() {
        return new MatchingStatsRequest(
                "WEEKLY", LocalDate.now().minusWeeks(4), LocalDate.now(),
                null, 10, 0, "RECENT", "DESC", false, true, "Asia/Seoul"
        );
    }

    public static MatchingStatsRequest monthly() {
        return new MatchingStatsRequest(
                "MONTHLY", LocalDate.now().minusMonths(6), LocalDate.now(),
                null, 10, 0, "RECENT", "DESC", false, true, "Asia/Seoul"
        );
    }

    public static MatchingStatsRequest realtime() {
        return new MatchingStatsRequest(
                "REALTIME", null, null,
                null, 10, 0, "RECENT", "DESC", false, true, "Asia/Seoul"
        );
    }

    public static MatchingStatsRequest forCategory(Long categoryId) {
        return new MatchingStatsRequest(
                "DAILY", LocalDate.now().minusDays(7), LocalDate.now(),
                categoryId, 10, 0, "RECENT", "DESC", false, true, "Asia/Seoul"
        );
    }

    public boolean isRealtimeRequest() {
        return "REALTIME".equals(period);
    }

    public boolean hasDateRange() {
        return startDate != null && endDate != null;
    }

    public boolean hasCategoryFilter() {
        return categoryId != null;
    }

    public boolean shouldIncludeInactive() {
        return Boolean.TRUE.equals(includeInactive);
    }

    public boolean shouldIncludeTrends() {
        return Boolean.TRUE.equals(includeTrends);
    }

    public LocalDateTime getStartDateTime() {
        return startDate != null ? startDate.atStartOfDay() : null;
    }

    public LocalDateTime getEndDateTime() {
        return endDate != null ? endDate.atTime(23, 59, 59) : null;
    }

    public PeriodType getPeriodType() {
        return PeriodType.valueOf(period);
    }

    public SortCriteria getSortCriteria() {
        return new SortCriteria(SortBy.valueOf(sortBy), SortOrder.valueOf(sortOrder));
    }

    // 🎯 Builder-like 패턴 (Immutable한 수정)
    public MatchingStatsRequest withCategoryId(Long categoryId) {
        return new MatchingStatsRequest(
                period, startDate, endDate, categoryId, limit, offset,
                sortBy, sortOrder, includeInactive, includeTrends, timezone
        );
    }

    public MatchingStatsRequest withDateRange(LocalDate start, LocalDate end) {
        return new MatchingStatsRequest(
                period, start, end, categoryId, limit, offset,
                sortBy, sortOrder, includeInactive, includeTrends, timezone
        );
    }

    public MatchingStatsRequest withPagination(int limit, int offset) {
        return new MatchingStatsRequest(
                period, startDate, endDate, categoryId, limit, offset,
                sortBy, sortOrder, includeInactive, includeTrends, timezone
        );
    }

    public MatchingStatsRequest withSort(String sortBy, String sortOrder) {
        return new MatchingStatsRequest(
                period, startDate, endDate, categoryId, limit, offset,
                sortBy, sortOrder, includeInactive, includeTrends, timezone
        );
    }

    public MatchingStatsRequest withTrends(boolean includeTrends) {
        return new MatchingStatsRequest(
                period, startDate, endDate, categoryId, limit, offset,
                sortBy, sortOrder, includeInactive, includeTrends, timezone
        );
    }

}
