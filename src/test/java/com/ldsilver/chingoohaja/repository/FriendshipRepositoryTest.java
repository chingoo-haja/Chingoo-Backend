package com.ldsilver.chingoohaja.repository;

import com.ldsilver.chingoohaja.domain.friendship.Friendship;
import com.ldsilver.chingoohaja.domain.friendship.enums.FriendshipStatus;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.domain.user.enums.Gender;
import com.ldsilver.chingoohaja.domain.user.enums.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("FriendshipRepository 테스트")
class FriendshipRepositoryTest {

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private UserRepository userRepository;

    private User user1;
    private User user2;
    private User user3;

    @BeforeEach
    void setUp() {
        friendshipRepository.deleteAll();
        userRepository.deleteAll();

        user1 = userRepository.save(User.of("user1@test.com", "유저1닉", "유저1",
                Gender.MALE, LocalDate.of(1990, 1, 1), null,
                UserType.USER, null, "kakao", "k1"));
        user2 = userRepository.save(User.of("user2@test.com", "유저2닉", "유저2",
                Gender.FEMALE, LocalDate.of(1992, 5, 10), null,
                UserType.USER, null, "kakao", "k2"));
        user3 = userRepository.save(User.of("user3@test.com", "유저3닉", "유저3",
                Gender.MALE, LocalDate.of(1988, 12, 25), null,
                UserType.USER, null, "google", "g1"));
    }

    @Nested
    @DisplayName("findFriendshipBetweenUsers")
    class FindFriendshipBetweenUsers {

        @Test
        @DisplayName("두 사용자 간의 특정 상태 친구 관계를 조회한다")
        void givenAcceptedFriendship_whenFind_thenReturnsFriendship() {
            // given
            friendshipRepository.save(Friendship.of(user1, user2, FriendshipStatus.ACCEPTED));

            // when
            Optional<Friendship> result = friendshipRepository
                    .findFriendshipBetweenUsers(user1, user2, FriendshipStatus.ACCEPTED);

            // then
            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("양방향으로 조회 가능하다 (user2, user1 순서로도 조회)")
        void givenReversedOrder_whenFind_thenReturnsFriendship() {
            // given
            friendshipRepository.save(Friendship.of(user1, user2, FriendshipStatus.ACCEPTED));

            // when - 역방향 조회
            Optional<Friendship> result = friendshipRepository
                    .findFriendshipBetweenUsers(user2, user1, FriendshipStatus.ACCEPTED);

            // then
            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("다른 상태의 관계는 조회되지 않는다")
        void givenDifferentStatus_whenFind_thenReturnsEmpty() {
            // given
            friendshipRepository.save(Friendship.of(user1, user2, FriendshipStatus.PENDING));

            // when
            Optional<Friendship> result = friendshipRepository
                    .findFriendshipBetweenUsers(user1, user2, FriendshipStatus.ACCEPTED);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findFriendshipBetweenUsersAnyStatus")
    class FindFriendshipBetweenUsersAnyStatus {

        @Test
        @DisplayName("상태에 관계없이 두 사용자 간의 관계를 조회한다")
        void givenAnyStatus_whenFind_thenReturnsFriendship() {
            // given
            friendshipRepository.save(Friendship.of(user1, user2, FriendshipStatus.BLOCKED));

            // when
            Optional<Friendship> result = friendshipRepository
                    .findFriendshipBetweenUsersAnyStatus(user1, user2);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getFriendshipStatus()).isEqualTo(FriendshipStatus.BLOCKED);
        }

        @Test
        @DisplayName("관계가 없으면 빈 결과를 반환한다")
        void givenNoRelationship_whenFind_thenReturnsEmpty() {
            Optional<Friendship> result = friendshipRepository
                    .findFriendshipBetweenUsersAnyStatus(user1, user3);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByAddresseeAndFriendshipStatusOrderByCreatedAtDesc")
    class FindByAddressee {

        @Test
        @DisplayName("받은 친구 요청을 조회한다")
        void givenPendingRequests_whenFind_thenReturnsPendingForAddressee() {
            // given
            friendshipRepository.save(Friendship.of(user1, user2, FriendshipStatus.PENDING));
            friendshipRepository.save(Friendship.of(user3, user2, FriendshipStatus.PENDING));

            // when
            List<Friendship> result = friendshipRepository
                    .findByAddresseeAndFriendshipStatusOrderByCreatedAtDesc(user2, FriendshipStatus.PENDING);

            // then
            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("findByRequesterAndFriendshipStatusOrderByCreatedAtDesc")
    class FindByRequester {

        @Test
        @DisplayName("보낸 친구 요청을 조회한다")
        void givenSentRequests_whenFind_thenReturnsPendingByRequester() {
            // given
            friendshipRepository.save(Friendship.of(user1, user2, FriendshipStatus.PENDING));
            friendshipRepository.save(Friendship.of(user1, user3, FriendshipStatus.PENDING));

            // when
            List<Friendship> result = friendshipRepository
                    .findByRequesterAndFriendshipStatusOrderByCreatedAtDesc(user1, FriendshipStatus.PENDING);

            // then
            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("findBlockedUserIds")
    class FindBlockedUserIds {

        @Test
        @DisplayName("차단한 사용자 ID 목록을 반환한다")
        void givenBlockedUsers_whenFind_thenReturnsBlockedIds() {
            // given
            friendshipRepository.save(Friendship.of(user1, user2, FriendshipStatus.BLOCKED));
            friendshipRepository.save(Friendship.of(user1, user3, FriendshipStatus.BLOCKED));

            // when
            List<Long> result = friendshipRepository.findBlockedUserIds(user1.getId());

            // then
            assertThat(result).hasSize(2);
            assertThat(result).containsExactlyInAnyOrder(user2.getId(), user3.getId());
        }

        @Test
        @DisplayName("상대방이 차단한 경우에도 조회된다")
        void givenBlockedByOther_whenFind_thenReturnsBlockerId() {
            // given - user2가 user1을 차단
            friendshipRepository.save(Friendship.of(user2, user1, FriendshipStatus.BLOCKED));

            // when - user1 입장에서 조회
            List<Long> result = friendshipRepository.findBlockedUserIds(user1.getId());

            // then
            assertThat(result).hasSize(1);
            assertThat(result).contains(user2.getId());
        }

        @Test
        @DisplayName("차단 관계가 없으면 빈 리스트를 반환한다")
        void givenNoBlocked_whenFind_thenReturnsEmpty() {
            List<Long> result = friendshipRepository.findBlockedUserIds(user1.getId());

            assertThat(result).isEmpty();
        }
    }
}
