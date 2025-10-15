package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.CallStatistics;
import com.ldsilver.chingoohaja.domain.call.enums.CallStatus;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.dto.user.response.CallHistoryResponse;
import com.ldsilver.chingoohaja.repository.CallRepository;
import com.ldsilver.chingoohaja.repository.CallStatisticsRepository;
import com.ldsilver.chingoohaja.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CallHistoryService {

    private final UserRepository userRepository;
    private final CallRepository callRepository;
    private final CallStatisticsRepository callStatisticsRepository;

    public CallHistoryResponse getCallHistory(Long userId, int page, int limit, String period) {
        log.debug("통화 이력 조회 - userId: {}, page: {}, limit: {}, period: {}",
                userId, page, limit, period);

        // 입력값 검증
        if (page < 1) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "페이지는 1 이상이어야 합니다.");
        }
        if (limit < 1 || limit > 100) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "limit은 1~100 사이여야 합니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 기간 필터링
        DateRange dateRange = getDateRangeByPeriod(period);

        // 페이징 설정 (최신순 정렬)
        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt"));

        // 통화 이력 조회
        Page<Call> callPage;
        if (dateRange == null) {
            // period가 "all"인 경우 - 전체 기간
            callPage = callRepository.findByUserAndStatus(user, CallStatus.COMPLETED, pageable);
        } else {
            // 특정 기간 필터링
            callPage = callRepository.findByUserAndStatusAndDateBetween(
                    user, CallStatus.COMPLETED, dateRange.start(), dateRange.end(), pageable);
        }

        // CallStatistics 일괄 조회 (N+1 방지)
        List<Call> calls = callPage.getContent();
        Map<Long, CallStatistics> statisticsMap = getStatisticsMap(calls, userId);

        // DTO 변환
        List<CallHistoryResponse.CallHistoryItem> historyItems = calls.stream()
                .map(call -> {
                    CallStatistics statistics = statisticsMap.get(call.getId());
                    return CallHistoryResponse.CallHistoryItem.from(call, userId, statistics);
                })
                .collect(Collectors.toList());

        // 페이지네이션 정보
        CallHistoryResponse.Pagination pagination = CallHistoryResponse.Pagination.of(
                page, callPage.getTotalElements(), limit);

        log.info("통화 이력 조회 완료 - userId: {}, totalCount: {}, currentPage: {}/{}",
                userId, callPage.getTotalElements(), page, pagination.totalPages());

        return CallHistoryResponse.of(historyItems, pagination);
    }

    private Map<Long, CallStatistics> getStatisticsMap(List<Call> calls, Long userId) {
        if (calls.isEmpty()) {
            return Map.of();
        }

        List<Long> callIds = calls.stream()
                .map(Call::getId)
                .collect(Collectors.toList());

        // 일괄 조회
        List<CallStatistics> statisticsList = new ArrayList<>();
        for (Long callId : callIds) {
            callStatisticsRepository.findByCallIdAndUserId(callId, userId)
                    .ifPresent(statisticsList::add);
        }

        return statisticsList.stream()
                .collect(Collectors.toMap(
                        stat -> stat.getCall().getId(),
                        stat -> stat
                ));
    }

    private DateRange getDateRangeByPeriod(String period) {
        if (period == null || "all".equalsIgnoreCase(period)) {
            return null; // 전체 기간
        }

        LocalDate today = LocalDate.now();
        LocalDateTime endDateTime = today.atTime(23, 59, 59);
        LocalDateTime startDateTime;

        switch (period.toLowerCase()) {
            case "week":
                startDateTime = today.minusDays(6).atStartOfDay(); // 최근 7일
                break;
            case "month":
                startDateTime = today.minusMonths(1).atStartOfDay(); // 최근 1개월
                break;
            case "quarter":
                int quarter = (today.getMonthValue() - 1) / 3 + 1;
                int startMonth = (quarter - 1) * 3 + 1;
                LocalDate quarterStart = LocalDate.of(today.getYear(), startMonth, 1);
                startDateTime = quarterStart.atStartOfDay(); // 현재 분기
                break;
            default:
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                        "period는 week, month, quarter, all 중 하나여야 합니다.");
        }

        return new DateRange(startDateTime, endDateTime);
    }

    private record DateRange(LocalDateTime start, LocalDateTime end) {}
}