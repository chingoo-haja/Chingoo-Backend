package com.ldsilver.chingoohaja.repository;

import com.ldsilver.chingoohaja.domain.category.Category;
import com.ldsilver.chingoohaja.domain.category.enums.CategoryType;
import com.ldsilver.chingoohaja.domain.matching.MatchingQueue;
import com.ldsilver.chingoohaja.domain.matching.enums.QueueStatus;
import com.ldsilver.chingoohaja.domain.matching.enums.QueueType;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.domain.user.enums.Gender;
import com.ldsilver.chingoohaja.domain.user.enums.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("MatchingQueueRepository 테스트")
class MatchingQueueRepositoryTest {

    @Autowired private MatchingQueueRepository matchingQueueRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TestEntityManager entityManager;

    private User user1;
    private User user2;
    private Category category1;
    private Category category2;

    @BeforeEach
    void setUp() {
        matchingQueueRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();

        user1 = userRepository.save(User.of("user1@test.com", "유저1닉", "유저1",
                Gender.MALE, LocalDate.of(1990, 1, 1), null,
                UserType.USER, null, "kakao", "k1"));
        user2 = userRepository.save(User.of("user2@test.com", "유저2닉", "유저2",
                Gender.FEMALE, LocalDate.of(1992, 5, 10), null,
                UserType.USER, null, "kakao", "k2"));

        category1 = categoryRepository.save(Category.of("일상대화", true, CategoryType.RANDOM));
        category2 = categoryRepository.save(Category.of("고민상담", true, CategoryType.GUARDIAN));
    }

    private void setCreatedAt(Object entity, LocalDateTime createdAt) {
        try {
            Field field = entity.getClass().getSuperclass().getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(entity, createdAt);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("findWaitingQueuesByCategory")
    class FindWaitingQueuesByCategory {

        @Test
        @DisplayName("특정 카테고리의 대기 중인 큐를 반환한다")
        void givenWaitingQueues_whenFind_thenReturnsWaitingOnly() {
            // given
            matchingQueueRepository.save(MatchingQueue.of(user1, category1,
                    QueueType.RANDOM_MATCH, QueueStatus.WAITING, "q1"));
            matchingQueueRepository.save(MatchingQueue.of(user2, category1,
                    QueueType.RANDOM_MATCH, QueueStatus.MATCHING, "q2"));

            // when
            List<MatchingQueue> result = matchingQueueRepository
                    .findWaitingQueuesByCategory(category1, QueueType.RANDOM_MATCH);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getQueueId()).isEqualTo("q1");
        }

        @Test
        @DisplayName("다른 카테고리의 큐는 포함하지 않는다")
        void givenDifferentCategory_whenFind_thenExcludes() {
            matchingQueueRepository.save(MatchingQueue.of(user1, category1,
                    QueueType.RANDOM_MATCH, QueueStatus.WAITING, "q1"));
            matchingQueueRepository.save(MatchingQueue.of(user2, category2,
                    QueueType.RANDOM_MATCH, QueueStatus.WAITING, "q2"));

            List<MatchingQueue> result = matchingQueueRepository
                    .findWaitingQueuesByCategory(category1, QueueType.RANDOM_MATCH);

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("findExpiredQueues")
    class FindExpiredQueues {

        @Test
        @DisplayName("만료 시간 이전에 생성된 대기 큐를 반환한다")
        void givenExpiredQueues_whenFind_thenReturnsExpired() {
            // given
            MatchingQueue queue = MatchingQueue.of(user1, category1,
                    QueueType.RANDOM_MATCH, QueueStatus.WAITING, "q-old");
            matchingQueueRepository.save(queue);
            entityManager.flush();

            // createdAt은 updatable=false이므로 native query로 변경
            entityManager.getEntityManager()
                    .createNativeQuery("UPDATE matching_queue SET created_at = :past WHERE id = :id")
                    .setParameter("past", LocalDateTime.now().minusHours(2))
                    .setParameter("id", queue.getId())
                    .executeUpdate();
            entityManager.clear();

            // when
            List<MatchingQueue> result = matchingQueueRepository
                    .findExpiredQueues(LocalDateTime.now().minusMinutes(30));

            // then
            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("updateExpiredQueues")
    class UpdateExpiredQueues {

        @Test
        @DisplayName("만료된 대기 큐의 상태를 일괄 업데이트한다")
        void givenExpiredQueues_whenUpdate_thenReturnsUpdatedCount() {
            // given
            MatchingQueue queue = MatchingQueue.of(user1, category1,
                    QueueType.RANDOM_MATCH, QueueStatus.WAITING, "q-expired");
            matchingQueueRepository.save(queue);
            entityManager.flush();

            // createdAt은 updatable=false이므로 native query로 변경
            entityManager.getEntityManager()
                    .createNativeQuery("UPDATE matching_queue SET created_at = :past WHERE id = :id")
                    .setParameter("past", LocalDateTime.now().minusHours(2))
                    .setParameter("id", queue.getId())
                    .executeUpdate();
            entityManager.clear();

            // when
            int updated = matchingQueueRepository.updateExpiredQueues(
                    QueueStatus.EXPIRED, LocalDateTime.now().minusMinutes(30));

            // then
            assertThat(updated).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("countWaitingQueues")
    class CountWaitingQueues {

        @Test
        @DisplayName("대기 중인 총 인원 수를 반환한다")
        void givenWaitingQueues_whenCount_thenReturnsTotal() {
            matchingQueueRepository.save(MatchingQueue.of(user1, category1,
                    QueueType.RANDOM_MATCH, QueueStatus.WAITING, "q1"));
            matchingQueueRepository.save(MatchingQueue.of(user2, category2,
                    QueueType.GUARDIAN_CALL, QueueStatus.WAITING, "q2"));

            long count = matchingQueueRepository.countWaitingQueues();

            assertThat(count).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("countWaitingQueuesByCategory")
    class CountWaitingQueuesByCategory {

        @Test
        @DisplayName("카테고리별 대기 인원 수를 반환한다")
        void givenWaitingQueues_whenCount_thenReturnsByCategory() {
            matchingQueueRepository.save(MatchingQueue.of(user1, category1,
                    QueueType.RANDOM_MATCH, QueueStatus.WAITING, "q1"));
            matchingQueueRepository.save(MatchingQueue.of(user2, category1,
                    QueueType.RANDOM_MATCH, QueueStatus.WAITING, "q2"));

            List<Object[]> result = matchingQueueRepository.countWaitingQueuesByCategory();

            assertThat(result).hasSize(1);
            assertThat(result.get(0)[0]).isEqualTo("일상대화");
            assertThat(result.get(0)[1]).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("getMatchingSuccessRate")
    class GetMatchingSuccessRate {

        @Test
        @DisplayName("매칭 성공률 통계를 반환한다")
        void givenQueues_whenGetRate_thenReturnsStats() {
            matchingQueueRepository.save(MatchingQueue.of(user1, category1,
                    QueueType.RANDOM_MATCH, QueueStatus.MATCHING, "q1"));
            matchingQueueRepository.save(MatchingQueue.of(user2, category1,
                    QueueType.RANDOM_MATCH, QueueStatus.WAITING, "q2"));

            LocalDateTime start = LocalDateTime.now().minusDays(1);
            LocalDateTime end = LocalDateTime.now().plusDays(1);

            List<Object[]> result = matchingQueueRepository.getMatchingSuccessRate(start, end);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)[0]).isEqualTo(1L); // matched
            assertThat(result.get(0)[1]).isEqualTo(2L); // total
        }
    }

    @Nested
    @DisplayName("cancelUserWaitingQueue")
    class CancelUserWaitingQueue {

        @Test
        @DisplayName("사용자의 대기 중인 큐를 취소한다")
        void givenWaitingQueue_whenCancel_thenReturnsUpdatedCount() {
            matchingQueueRepository.save(MatchingQueue.of(user1, category1,
                    QueueType.RANDOM_MATCH, QueueStatus.WAITING, "q1"));

            int cancelled = matchingQueueRepository.cancelUserWaitingQueue(user1);

            assertThat(cancelled).isEqualTo(1);
        }

        @Test
        @DisplayName("대기 중이 아닌 큐는 취소하지 않는다")
        void givenNonWaitingQueue_whenCancel_thenReturnsZero() {
            matchingQueueRepository.save(MatchingQueue.of(user1, category1,
                    QueueType.RANDOM_MATCH, QueueStatus.MATCHING, "q1"));

            int cancelled = matchingQueueRepository.cancelUserWaitingQueue(user1);

            assertThat(cancelled).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("findByQueueId")
    class FindByQueueId {

        @Test
        @DisplayName("queueId로 매칭 큐를 조회한다")
        void givenQueueId_whenFind_thenReturnsQueue() {
            matchingQueueRepository.save(MatchingQueue.of(user1, category1,
                    QueueType.RANDOM_MATCH, QueueStatus.WAITING, "unique-queue-id"));

            Optional<MatchingQueue> result = matchingQueueRepository.findByQueueId("unique-queue-id");

            assertThat(result).isPresent();
            assertThat(result.get().getUser().getId()).isEqualTo(user1.getId());
        }

        @Test
        @DisplayName("존재하지 않는 queueId면 빈 결과를 반환한다")
        void givenNonExistingId_whenFind_thenReturnsEmpty() {
            Optional<MatchingQueue> result = matchingQueueRepository.findByQueueId("non-existing");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("countByCategoryIdAndStatus")
    class CountByCategoryIdAndStatus {

        @Test
        @DisplayName("카테고리별 특정 상태의 큐 수를 반환한다")
        void givenQueues_whenCount_thenReturnsCount() {
            matchingQueueRepository.save(MatchingQueue.of(user1, category1,
                    QueueType.RANDOM_MATCH, QueueStatus.WAITING, "q1"));
            matchingQueueRepository.save(MatchingQueue.of(user2, category1,
                    QueueType.RANDOM_MATCH, QueueStatus.WAITING, "q2"));

            long count = matchingQueueRepository.countByCategoryIdAndStatus(
                    category1.getId(), QueueStatus.WAITING);

            assertThat(count).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("getBatchCategorySuccessRates")
    class GetBatchCategorySuccessRates {

        @Test
        @DisplayName("여러 카테고리의 매칭 성공률을 한 번에 조회한다")
        void givenMultipleCategories_whenGet_thenReturnsBatchResults() {
            matchingQueueRepository.save(MatchingQueue.of(user1, category1,
                    QueueType.RANDOM_MATCH, QueueStatus.MATCHING, "q1"));
            matchingQueueRepository.save(MatchingQueue.of(user2, category2,
                    QueueType.GUARDIAN_CALL, QueueStatus.WAITING, "q2"));

            LocalDateTime start = LocalDateTime.now().minusDays(1);
            LocalDateTime end = LocalDateTime.now().plusDays(1);

            List<Object[]> result = matchingQueueRepository.getBatchCategorySuccessRates(
                    List.of(category1.getId(), category2.getId()), start, end);

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("cancelMatchingQueueByQueueId")
    class CancelMatchingQueueByQueueId {

        @Test
        @DisplayName("queueId로 대기 중인 큐를 취소한다")
        void givenQueueId_whenCancel_thenReturnsUpdatedCount() {
            matchingQueueRepository.save(MatchingQueue.of(user1, category1,
                    QueueType.RANDOM_MATCH, QueueStatus.WAITING, "cancel-target"));

            int cancelled = matchingQueueRepository.cancelMatchingQueueByQueueId("cancel-target");

            assertThat(cancelled).isEqualTo(1);
        }
    }
}
