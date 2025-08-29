package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.enums.CallType;
import com.ldsilver.chingoohaja.domain.category.Category;
import com.ldsilver.chingoohaja.domain.matching.MatchingQueue;
import com.ldsilver.chingoohaja.domain.matching.enums.QueueStatus;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.repository.CallRepository;
import com.ldsilver.chingoohaja.repository.CategoryRepository;
import com.ldsilver.chingoohaja.repository.MatchingQueueRepository;
import com.ldsilver.chingoohaja.repository.UserRepository;
import com.ldsilver.chingoohaja.validation.MatchingValidatoinConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingSchedulerService {

    private final RedisMatchingQueueService redisMatchingQueueService;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final CallRepository callRepository;
    private final MatchingQueueRepository matchingQueueRepository;
    private final WebSocketEventService webSocketEventService;

    @Scheduled(fixedDelay = MatchingValidatoinConstants.Scheduler.DEFAULT_MATCHING_DELAY)
    @Transactional
    public void processMatching() {
        log.debug("하이브리드 매칭 스케줄러 실행");

        try {
            List<Category> activeCategories = categoryRepository.findByIsActiveTrueOrderByName();

            for (Category category : activeCategories) {
                processMatchingForCategory(category);
            }
        } catch (Exception e) {
            log.error("매칭 스케줄러 실행 중 오류 발생", e);
        }

        log.debug("하이브리드 매칭 스케줄러 완료");
    }

    private void processMatchingForCategory(Category category) {
        try {
            // 1. 대기 인원 확인
            long waitingCount = redisMatchingQueueService.getWaitingCount(category.getId());

            if (waitingCount < 2) {
                log.debug("매칭 대기 인원 부족 - categoryId: {}, waiting: {}",
                        category.getId(), waitingCount);
                return;
            }

            // 2. 하이브리드 매칭 실행
            RedisMatchingQueueService.MatchResult matchResult =
                    redisMatchingQueueService.findMatchesHybrid(category.getId(), 2);

            if (!matchResult.success() || matchResult.userIds().size() < 2) {
                log.debug("하이브리드 매칭 실패 - categoryId: {}, reason: {}",
                        category.getId(), matchResult.message());
                return;
            }

            // 3. 매칭된 사용자들 검증
            List<Long> userIds = matchResult.userIds();
            Long user1Id = userIds.get(0);
            Long user2Id = userIds.get(1);

            Optional<User> user1Opt = userRepository.findById(user1Id);
            Optional<User> user2Opt = userRepository.findById(user2Id);

            if (user1Opt.isEmpty() || user2Opt.isEmpty()) {
                log.error("매칭된 사용자 조회 실패 - userId: {}, user2Id: {}", user1Id, user2Id);
                return;
            }

            User user1 = user1Opt.get();
            User user2 = user2Opt.get();

            // 4. Call 엔티티 생성
            Call call = Call.from(user1, user2, category, CallType.RANDOM_MATCH);
            call.startCall();
            Call savedCall = callRepository.save(call);

            // 5. DB 매칭 큐 상태 업데이트 (WAITING -> MATCHING)
            updateMatchingQueueStatus(userIds, QueueStatus.MATCHING);

            // 6. 통화방 입장용 세션 토큰 생성
            String sessionToken = generateSessionToken();

            // 7. WebSocket 매칭 성공 알림 전송
            notifyMatchingSuccess(user1, user2, savedCall, category, sessionToken);

            log.debug("매칭 성공 완료 - categoryId: {}, callId: {}, users: [{}, {}]",
                    category.getId(), savedCall.getId(), user1Id, user2Id);

        } catch (Exception e) {
            log.error("카테고리 매칭 처리 실패 - categoryId: {}", category.getId(), e);
        }
    }

    private void updateMatchingQueueStatus(List<Long> userIds, QueueStatus newStatus) {
        try {
            for (Long userId: userIds) {
                User user = userRepository.findById(userId).orElse(null);
                if (user != null) {
                    List<MatchingQueue> waitingQueues = matchingQueueRepository
                            .findByUserOrderByCreatedAtDesc(user)
                            .stream()
                            .filter(q -> q.getQueueStatus() == QueueStatus.WAITING)
                            .limit(1)
                            .toList();

                    for (MatchingQueue queue : waitingQueues) {
                        if (newStatus == QueueStatus.MATCHING) {
                            queue.startMatching();;
                        }
                        matchingQueueRepository.save(queue);
                        log.debug("매칭 큐 상태 업데이트 - userId: {}, queueId: {}, status: {}",
                                userId, queue.getQueueId(), newStatus);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("매칭 큐 상태 업데이트 실패 - userIds: {}", userIds, e);
        }
    }

    private void notifyMatchingSuccess(User user1, User user2, Call call, Category category, String sessionToken) {
        try {
            // 각 사용자에게 상대방 정보와 함께 매칭 성공 알림
            webSocketEventService.sendMatchingSuccessNotification(
                    user1.getId(), call.getId(), user2.getId(), user2.getNickname()
            );

            webSocketEventService.sendMatchingSuccessNotification(
                    user2.getId(), call.getId(), user1.getId(), user1.getNickname()
            );

            log.debug("매칭 성공 알림 전송 완료 - callId: {}, users: [{}, {}]",
                    call.getId(), user1.getId(), user2.getId());
        } catch (Exception e) {
            log.error("매칭 성공 알림 전송 실패 - callId: {}", call.getId(), e);
        }

    }


    private String generateSessionToken() {
        return "session_" + UUID.randomUUID().toString().replace("-", "");
    }


    /**
     * 만료된 대기열 정리 - 1분마다 실행
     * DB 이력을 위한 별도 실행
     */
    @Scheduled(fixedDelay = MatchingValidatoinConstants.Scheduler.DEFAULT_CLEANUP_DELAY)
    @Transactional
    public void cleanupExpiredQueues() {
        try {
            LocalDateTime expiredTime = LocalDateTime.now().minusSeconds(MatchingValidatoinConstants.Scheduler.DEFAULT_TTL_SECONDS); //10분 전

            List<MatchingQueue> expiredQueues = matchingQueueRepository.findExpiredQueues(expiredTime);

            if (!expiredQueues.isEmpty()) {
                int updatedCount = matchingQueueRepository.updateExpiredQueues(
                        QueueStatus.EXPIRED, expiredTime);

                for (MatchingQueue queue : expiredQueues) {
                    sendExpirationNotification(queue.getUser().getId());
                }
                log.debug("만료된 매칭 큐 정리 완료 - 정리된 큐 수: {}", updatedCount);
            }
        } catch (Exception e) {
            log.error("만료된 매칭 큐 정리 실패", e);
        }
    }

    private void sendExpirationNotification(Long userId) {
        try {
            webSocketEventService.sendMatchingCancelledNotification(
                    userId, "매칭 대기 시간이 만료되었습니다."
            );
        } catch (Exception e) {
            log.warn("만료 알림 전송 실패 - userId: {}", userId, e);
        }
    }

    /**
     * 매칭 통계 갱신 - 매일 자정 실행
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional(readOnly = true)
    public void updateDailyMatchingStatistics() {
        try {
            LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
            LocalDateTime today = LocalDateTime.now();

            // 일일 매칭 성공률 계산
            List<Object[]> dailyStats = matchingQueueRepository.getMatchingSuccessRate(yesterday, today);

            if (!dailyStats.isEmpty()) {
                Object[] stats = dailyStats.get(0);
                long matchedCount = ((Number) stats[0]).longValue();
                long totalCount = ((Number) stats[1]).longValue();
                double successRate = totalCount > 0 ? (double) matchedCount / totalCount * 100 : 0.0;

                log.info("어제 매칭 통계 - 전체: {}, 성공: {}, 성공률: {:.2f}%",
                        totalCount, matchedCount, successRate);
            }

        } catch (Exception e) {
            log.error("일일 매칭 통계 갱신 실패", e);
        }
    }
}
