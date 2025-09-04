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
import com.ldsilver.chingoohaja.validation.MatchingValidationConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
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

    @Scheduled(fixedDelay = MatchingValidationConstants.Scheduler.DEFAULT_MATCHING_DELAY)
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
            RedisMatchingQueueService.MatchCandicateResult candidateResult =
                    redisMatchingQueueService.findMatchCandidates(category.getId(), 2);

            if (!candidateResult.success() || candidateResult.userIds().size() < 2) {
                log.debug("하이브리드 매칭 실패 - categoryId: {}, reason: {}",
                        category.getId(), candidateResult.message());
                return;
            }

            // 3. 매칭된 사용자들 검증
            List<Long> userIds = candidateResult.userIds();
            Long user1Id = userIds.get(0);
            Long user2Id = userIds.get(1);

            Optional<User> user1Opt = userRepository.findById(user1Id);
            Optional<User> user2Opt = userRepository.findById(user2Id);

            if (user1Opt.isEmpty() || user2Opt.isEmpty()) {
                log.error("매칭된 사용자 조회 실패 - user1Id: {}, user2Id: {}, user1Exists: {}, user2Exists: {}",
                        user1Id, user2Id, user1Opt.isPresent(), user2Opt.isPresent());
                return;
            }

            User user1 = user1Opt.get();
            User user2 = user2Opt.get();

            // 4. Call 엔티티 생성
            Call savedCall = createCallFromMatchedUsers(user1, user2, category);

            if (savedCall == null) {
                log.error("Call 생성 실패 - categoryId: {}, userIds: {}", category.getId(), userIds);
                // Call 생성 실패 시 Redis 큐에서 사용자를 제거할지 아니면 다시 시도할지 결정 필요
                return;
            }

            // 5. DB 성공했을 때만 Redis에서 사용자 제거
            RedisMatchingQueueService.RemoveUserResult removeResult =
                    redisMatchingQueueService.removeMatchedUsers(category.getId(), userIds);

            if (!removeResult.success()) {
                log.warn("Redis 사용자 제거 실패 - categoryId: {}, userIds: {}, reason: {}",
                        category.getId(), userIds, removeResult.message());
                // DB는 성공했으므로 매칭은 진행하되, Redis 정리만 실패한 상황
            }

            // 6. DB 매칭 큐 상태 업데이트 (WAITING -> MATCHING)
            updateMatchingQueueStatus(userIds, QueueStatus.MATCHING);

            // 7. 통화방 입장용 세션 토큰 생성
            String sessionToken = generateSessionToken();

            // 8. WebSocket 매칭 성공 알림 전송
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            sendMatchingSuccessNotification(user1, user2, savedCall);
                        }
                    }
            );

            log.debug("매칭 성공 완료 - categoryId: {}, callId: {}, users: {}",
                    category.getId(), savedCall.getId(), userIds);

        } catch (Exception e) {
            log.error("카테고리 매칭 처리 실패 - categoryId: {}", category.getId(), e);
        }
    }

    private Call createCallFromMatchedUsers(User user1, User user2, Category category) {
        try {
            Call call = Call.from(user1, user2, category, CallType.RANDOM_MATCH);
            call.startCall();

            return callRepository.save(call);
        } catch (Exception e) {
            log.error("Call 생성 실패 - user1Id: {}, user2Id: {}", user1.getId(), user2.getId(), e);
            return null;
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
                            queue.startMatching();
                        }
                        matchingQueueRepository.save(queue);
                        log.debug("매칭 큐 상태 업데이트 - userId: {}, queueId: {}, status: {}",
                                userId, queue.getQueueId(), newStatus);
                    }
                }
            }
        } catch (Exception e) {
            log.error("매칭 큐 상태 업데이트 실패 - userIds: {}", userIds, e);
            throw e;
        }
    }

    private void sendMatchingSuccessNotification(User user1, User user2, Call call) {
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
    @Scheduled(fixedDelay = MatchingValidationConstants.Scheduler.DEFAULT_CLEANUP_DELAY)
    @Transactional
    public void cleanupExpiredQueues() {
        try {
            LocalDateTime expiredTime = LocalDateTime.now().minusSeconds(MatchingValidationConstants.Scheduler.DEFAULT_EXPIRED_TIME); //10분 전

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
            LocalDate todayDate = LocalDate.now();
            LocalDateTime yesterday = todayDate.minusDays(1).atStartOfDay();
            LocalDateTime today = todayDate.atStartOfDay();

            // 일일 매칭 성공률 계산
            List<Object[]> dailyStats = matchingQueueRepository.getMatchingSuccessRate(yesterday, today);

            if (!dailyStats.isEmpty()) {
                Object[] stats = dailyStats.get(0);
                long matchedCount = ((Number) stats[0]).longValue();
                long totalCount = ((Number) stats[1]).longValue();
                double successRate = totalCount > 0 ? (double) matchedCount / totalCount * 100 : 0.0;

                log.info("어제 매칭 통계 - 전체: {}, 성공: {}, 성공률: {}%",
                        totalCount, matchedCount, String.format("%.2f", successRate));
            }

        } catch (Exception e) {
            log.error("일일 매칭 통계 갱신 실패", e);
        }
    }
}
