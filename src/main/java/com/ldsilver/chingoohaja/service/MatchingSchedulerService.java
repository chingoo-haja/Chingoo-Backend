package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.config.MatchingSchedulerProperties;
import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.enums.CallType;
import com.ldsilver.chingoohaja.domain.category.Category;
import com.ldsilver.chingoohaja.domain.matching.MatchingQueue;
import com.ldsilver.chingoohaja.domain.matching.enums.QueueStatus;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.event.CallStartedEvent;
import com.ldsilver.chingoohaja.event.MatchingSuccessEvent;
import com.ldsilver.chingoohaja.repository.CallRepository;
import com.ldsilver.chingoohaja.repository.CategoryRepository;
import com.ldsilver.chingoohaja.repository.MatchingQueueRepository;
import com.ldsilver.chingoohaja.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.matching.scheduler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class MatchingSchedulerService {

    private final RedisMatchingQueueService redisMatchingQueueService;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final CallRepository callRepository;
    private final MatchingQueueRepository matchingQueueRepository;
    private final WebSocketEventService webSocketEventService;
    private final MatchingSchedulerProperties schedulerProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager transactionManager;

    @Scheduled(fixedDelayString = "#{@matchingSchedulerProperties.matchingDelay}")
    public void processMatching() {
        if (schedulerProperties.isDebugLogEnabled()) {
            log.debug("하이브리드 매칭 스케줄러 실행");
        }

        try {
            List<Category> activeCategories = categoryRepository.findByIsActiveTrueOrderByName();
            int processedCount = 0;

            for (Category category : activeCategories) {
                Boolean processed = new TransactionTemplate(transactionManager)
                        .execute(status -> processMatchingForCategory(category));
                if (Boolean.TRUE.equals(processed)) processedCount++;
            }

            // 처리된 매칭이 있을 때만 로그 출력 (로컬 환경 고려)
            if (processedCount > 0 || schedulerProperties.isDebugLogEnabled()) {
                log.info("매칭 스케줄러 완료 - 처리된 카테고리: {}/{}", processedCount, activeCategories.size());
            }

        } catch (Exception e) {
            log.error("매칭 스케줄러 실행 중 오류 발생", e);
        }
    }


    protected boolean processMatchingForCategory(Category category) {
        try {
            // 1. 대기 인원 확인
            long waitingCount = redisMatchingQueueService.getWaitingCount(category.getId());

            if (waitingCount < 2) {
                if (schedulerProperties.isDebugLogEnabled()){
                    log.debug("매칭 대기 인원 부족 - categoryId: {}, waiting: {}",
                            category.getId(), waitingCount);
                }
                return false;
            }

            log.info("매칭 처리 시작 - categoryId: {}, categoryName: {}, waiting: {}",
                    category.getId(), category.getName(), waitingCount);


            // 2. 하이브리드 매칭 실행
            int maxAttempts = 5;
            RedisMatchingQueueService.MatchCandicateResult candidateResult = null;


            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                candidateResult = redisMatchingQueueService.findMatchCandidates(category.getId(), 2);

                if (!candidateResult.success() || candidateResult.userIds().size() < 2) {
                    log.debug("하이브리드 매칭 실패 (시도 {}/{}) - categoryId: {}, reason: {}",
                            attempt + 1, maxAttempts, category.getId(), candidateResult.message());
                    continue;
                }

                // 3. 매칭된 사용자들 검증
                List<Long> userIds = candidateResult.userIds();
                Long user1Id = userIds.get(0);
                Long user2Id = userIds.get(1);

                if (user1Id.equals(user2Id)) {
                    log.warn("동일 사용자 매칭 감지 (시도 {}/{}) - categoryId: {}, userId: {}",
                            attempt + 1, maxAttempts, category.getId(), user1Id);
                    continue;
                }

                if (redisMatchingQueueService.isBlocked(user1Id, user2Id)) {
                    log.warn("차단된 사용자 매칭 방지 (시도 {}/{}) - categoryId: {}, user1: {}, user2: {}",
                            attempt + 1, maxAttempts, category.getId(), user1Id, user2Id);
                    continue;
                }

                Optional<User> user1Opt = userRepository.findById(user1Id);
                Optional<User> user2Opt = userRepository.findById(user2Id);

                if (user1Opt.isEmpty() || user2Opt.isEmpty()) {
                    log.error("매칭된 사용자 조회 실패 - user1Id: {}, user2Id: {}, user1Exists: {}, user2Exists: {}",
                            user1Id, user2Id, user1Opt.isPresent(), user2Opt.isPresent());
                    restoreUsersToQueue(userIds, category.getId());
                    continue;
                }

                User user1 = user1Opt.get();
                User user2 = user2Opt.get();

                log.info("유효한 매칭 발견 (시도 {}/{}) - categoryId: {}, users: [{}, {}]",
                        attempt + 1, maxAttempts, category.getId(), user1Id, user2Id);

                // 4. Call 엔티티 생성
                Call savedCall = createCallFromMatchedUsers(user1, user2, category);

                if (savedCall == null) {
                    log.error("Call 생성 실패 - categoryId: {}, userIds: {}", category.getId(), userIds);
                    restoreUsersToQueue(userIds, category.getId());
                    return false;
                }

                // 5. DB 매칭 큐 상태 업데이트 (WAITING -> MATCHING)
                updateMatchingQueueStatus(userIds, category.getId(), QueueStatus.MATCHING);

                // 6. 통화방 입장용 세션 토큰 생성
                String sessionToken = generateSessionToken();

                log.debug("매칭 성공 완료 - categoryId: {}, callId: {}, users: {}",
                        category.getId(), savedCall.getId(), userIds);

                // 7. 커밋 이후 처리: Redis 제거 → WebSocket 매칭 성공 알림 전송
                // 수정: 이벤트 발행 (트랜젝션 커밋 후 처리)
                eventPublisher.publishEvent(new MatchingSuccessEvent(
                        savedCall.getId(),
                        category.getId(),
                        userIds,
                        user1,
                        user2
                ));
                return true;
            }

            // 최대 시도 횟수 초과
            log.warn("유효한 매칭을 찾지 못함 - categoryId: {}, 최대 시도 횟수 초과 ({}회)",
                    category.getId(), maxAttempts);
            return false;

        } catch (Exception e) {
            log.error("카테고리 매칭 처리 실패 - categoryId: {}", category.getId(), e);
            return false;
        }
    }



    private void restoreUsersToQueue(List<Long> userIds, Long categoryId) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        log.info("매칭 실패로 인한 사용자 큐 복구 시작 - categoryId: {}, userIds: {}",categoryId, userIds);

        for (Long userId: userIds) {
            try {
                String newQueueId = generateQueueId(userId, categoryId);
                RedisMatchingQueueService.EnqueueResult result =
                        redisMatchingQueueService.enqueueUser(userId, categoryId, newQueueId);

                if (result.success()) {
                    log.debug("사용자 큐 복구 성공 - userId: {}, newQueueId: {}", userId, newQueueId);
                } else {
                    log.warn("사용자 큐 복구 실패 - userId: {}, reason: {}", userId, result.message());
                }
            } catch (Exception e) {
                log.error("사용자 큐 복구 중 예외 발생 - userId: {}", userId, e);
            }
        }
    }

    private Call createCallFromMatchedUsers(User user1, User user2, Category category) {
        try {
            Call call = Call.from(user1, user2, category, CallType.RANDOM_MATCH);
            call.startCall(); //상태만 변경
            Call savedCall = callRepository.save(call);

            if (savedCall.getAgoraChannelName() != null) {
                eventPublisher.publishEvent(new CallStartedEvent(
                        savedCall.getId(),
                        savedCall.getAgoraChannelName()
                ));
                log.debug("CallStartedEvent 발행 - callId: {}" , savedCall.getId());
            }

            return savedCall;
        } catch (Exception e) {
            log.error("Call 생성 실패 - user1Id: {}, user2Id: {}", user1.getId(), user2.getId(), e);
            return null;
        }
    }

    private void updateMatchingQueueStatus(List<Long> userIds, Long categoryId,QueueStatus newStatus) {
        try {
            for (Long userId: userIds) {
                User user = userRepository.findById(userId).orElse(null);
                if (user != null) {
                    List<MatchingQueue> waitingQueues = matchingQueueRepository
                            .findByUserOrderByCreatedAtDesc(user)
                            .stream()
                            .filter(q -> q.getQueueStatus() == QueueStatus.WAITING
                                                    && q.getCategory() != null
                                                    && q.getCategory().getId().equals(categoryId))
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

    private String generateQueueId(Long userId, Long categoryId) {
        return String.format("queue_%d_%d_%s", userId, categoryId,
                UUID.randomUUID().toString().substring(0, 8));
    }


    private void scheduleRedisCleanupRetry(Long categoryId, List<Long> userIds) {
        CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS).execute(() -> {
            try {
                log.info("Redis 정리 재시도 - categoryId: {}, userIds: {}", categoryId, userIds);
                RedisMatchingQueueService.RemoveUserResult retryResult =
                        redisMatchingQueueService.removeMatchedUsers(categoryId, userIds);

                if (retryResult.success()) {
                    log.info("Redis 정리 재시도 성공 - categoryId: {}", categoryId);
                } else {
                    log.warn("Redis 정리 재시도 실패 - categoryId: {}, reason: {}", categoryId, retryResult.message());
                }
            } catch (Exception e) {
                log.error("Redis 정리 재시도 중 예외 발생 - categoryId: {}", categoryId, e );
            }
        });
    }


    /**
     * 만료된 대기열 정리
     * DB 이력을 위한 별도 실행
     */
    @Scheduled(fixedDelayString = "#{@matchingSchedulerProperties.cleanupDelay}")
    @Transactional
    public void cleanupExpiredQueues() {
        try {
            LocalDateTime expiredTime = LocalDateTime.now()
                    .minusSeconds(schedulerProperties.getExpiredTime()); //10분 전

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
