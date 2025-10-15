package com.ldsilver.chingoohaja.dto.user.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.CallStatistics;
import com.ldsilver.chingoohaja.domain.user.User;

import java.time.LocalDateTime;
import java.util.List;

public record CallHistoryResponse(
        @JsonProperty("calls") List<CallHistoryItem> calls,
        @JsonProperty("pagination") Pagination pagination
) {
    public record CallHistoryItem(
            @JsonProperty("call_id") Long callId,
            @JsonProperty("partner_id") Long partnerId,
            @JsonProperty("partner_nickname") String partnerNickname,
            @JsonProperty("category_id") Long categoryId,
            @JsonProperty("category_name") String categoryName,
            @JsonProperty("started_at") LocalDateTime startedAt,
            @JsonProperty("ended_at") LocalDateTime endedAt,
            @JsonProperty("duration_minutes") int durationMinutes,
            @JsonProperty("average_network_quality") Double averageNetworkQuality,
            @JsonProperty("total_data_usage_mb") Double totalDataUsageMB
    ) {
        public static CallHistoryItem from(Call call, Long currentUserId, CallStatistics statistics) {
            User partner = call.getPartner(currentUserId);

            Integer durationSeconds = call.getDurationSeconds();
            int durationMinutes = durationSeconds != null ? durationSeconds / 60 : 0;

            Double networkQuality = statistics != null ? statistics.getAverageNetworkQuality() : null;
            Double dataUsageMB = statistics != null ?
                    statistics.getTotalBytes() / (1024.0 * 1024.0) : null;

            if (dataUsageMB != null) {
                dataUsageMB = Math.round(dataUsageMB * 100.0) / 100.0; // 소수점 2자리
            }
            if (networkQuality != null) {
                networkQuality = Math.round(networkQuality * 10.0) / 10.0; // 소수점 1자리
            }

            return new CallHistoryItem(
                    call.getId(),
                    partner.getId(),
                    partner.getNickname(),
                    call.getCategory().getId(),
                    call.getCategory().getName(),
                    call.getStartAt(),
                    call.getEndAt(),
                    durationMinutes,
                    networkQuality,
                    dataUsageMB
            );
        }
    }

    public record Pagination(
            @JsonProperty("current_page") int currentPage,
            @JsonProperty("total_pages") int totalPages,
            @JsonProperty("total_count") long totalCount,
            @JsonProperty("has_next") boolean hasNext
    ) {
        public static Pagination of(int currentPage, long totalCount, int pageSize) {
            int totalPages = (int) Math.ceil((double) totalCount / pageSize);
            boolean hasNext = currentPage < totalPages;

            return new Pagination(currentPage, totalPages, totalCount, hasNext);
        }
    }

    public static CallHistoryResponse of(List<CallHistoryItem> calls, Pagination pagination) {
        return new CallHistoryResponse(calls, pagination);
    }
}