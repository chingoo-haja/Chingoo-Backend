package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.friendship.Friendship;
import com.ldsilver.chingoohaja.domain.friendship.enums.FriendshipStatus;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.domain.user.enums.Gender;
import com.ldsilver.chingoohaja.domain.user.enums.UserType;
import com.ldsilver.chingoohaja.dto.friendship.response.FriendListResponse;
import com.ldsilver.chingoohaja.dto.friendship.response.PendingFriendRequestListResponse;
import com.ldsilver.chingoohaja.dto.friendship.response.SentFriendRequestListResponse;
import com.ldsilver.chingoohaja.repository.CallRepository;
import com.ldsilver.chingoohaja.repository.FriendshipRepository;
import com.ldsilver.chingoohaja.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FriendshipService 테스트")
class FriendshipServiceTest {

    @Mock private FriendshipRepository friendshipRepository;
    @Mock private UserRepository userRepository;
    @Mock private CallRepository callRepository;

    @InjectMocks private FriendshipService friendshipService;

    private User requester;
    private User addressee;

    @BeforeEach
    void setUp() {
        requester = User.of("req@test.com", "요청자", "요청자실명", Gender.MALE, LocalDate.of(1990, 1, 1), null, UserType.USER, null, "kakao", "k1");
        addressee = User.of("addr@test.com", "수신자", "수신자실명", Gender.FEMALE, LocalDate.of(1992, 5, 15), null, UserType.USER, null, "kakao", "k2");
        setId(requester, 1L);
        setId(addressee, 2L);
    }

    private void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Friendship createFriendship(FriendshipStatus status) {
        Friendship friendship = Friendship.of(requester, addressee, status);
        setId(friendship, 10L);
        return friendship;
    }

    @Nested
    @DisplayName("sendFriendRequest")
    class SendFriendRequest {

        @Test
        @DisplayName("정상적으로 친구 요청을 전송한다")
        void givenValidUsers_whenSendRequest_thenSavesAsPending() {
            // given
            when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
            when(userRepository.findByNickname("수신자")).thenReturn(Optional.of(addressee));
            when(userRepository.findById(2L)).thenReturn(Optional.of(addressee));
            when(friendshipRepository.findFriendshipBetweenUsers(any(), any(), eq(FriendshipStatus.BLOCKED)))
                    .thenReturn(Optional.empty());
            when(friendshipRepository.findFriendshipBetweenUsers(any(), any(), eq(FriendshipStatus.ACCEPTED)))
                    .thenReturn(Optional.empty());
            when(friendshipRepository.findFriendshipBetweenUsers(any(), any(), eq(FriendshipStatus.PENDING)))
                    .thenReturn(Optional.empty());

            // when
            friendshipService.sendFriendRequest(1L, "수신자");

            // then
            verify(friendshipRepository).save(any(Friendship.class));
        }

        @Test
        @DisplayName("자기 자신에게 친구 요청을 보낼 수 없다")
        void givenSameUser_whenSendRequest_thenThrowsException() {
            // given
            when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
            when(userRepository.findByNickname("요청자")).thenReturn(Optional.of(requester));

            // when & then
            assertThatThrownBy(() -> friendshipService.sendFriendRequest(1L, "요청자"))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.SELF_FRIENDSHIP_NOT_ALLOWED));
        }

        @Test
        @DisplayName("이미 친구인 사용자에게 요청을 보낼 수 없다")
        void givenAlreadyFriends_whenSendRequest_thenThrowsException() {
            // given
            when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
            when(userRepository.findByNickname("수신자")).thenReturn(Optional.of(addressee));
            when(userRepository.findById(2L)).thenReturn(Optional.of(addressee));
            when(friendshipRepository.findFriendshipBetweenUsers(any(), any(), eq(FriendshipStatus.BLOCKED)))
                    .thenReturn(Optional.empty());
            when(friendshipRepository.findFriendshipBetweenUsers(requester, addressee, FriendshipStatus.ACCEPTED))
                    .thenReturn(Optional.of(createFriendship(FriendshipStatus.ACCEPTED)));

            // when & then
            assertThatThrownBy(() -> friendshipService.sendFriendRequest(1L, "수신자"))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.FRIENDSHIP_ALREADY_EXISTS));
        }

        @Test
        @DisplayName("차단된 사용자에게 요청을 보낼 수 없다")
        void givenBlockedUser_whenSendRequest_thenThrowsException() {
            // given
            Friendship blockedFriendship = createFriendship(FriendshipStatus.BLOCKED);

            when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
            when(userRepository.findByNickname("수신자")).thenReturn(Optional.of(addressee));
            when(userRepository.findById(2L)).thenReturn(Optional.of(addressee));
            when(friendshipRepository.findFriendshipBetweenUsers(any(), any(), eq(FriendshipStatus.BLOCKED)))
                    .thenReturn(Optional.of(blockedFriendship));

            // when & then
            assertThatThrownBy(() -> friendshipService.sendFriendRequest(1L, "수신자"))
                    .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("acceptFriendRequest")
    class AcceptFriendRequest {

        @Test
        @DisplayName("받은 친구 요청을 수락한다")
        void givenPendingRequest_whenAccept_thenChangesToAccepted() {
            // given
            Friendship friendship = createFriendship(FriendshipStatus.PENDING);
            when(friendshipRepository.findById(10L)).thenReturn(Optional.of(friendship));
            when(friendshipRepository.save(any(Friendship.class))).thenReturn(friendship);

            // when
            friendshipService.acceptFriendRequest(2L, 10L);

            // then
            assertThat(friendship.isAccepted()).isTrue();
            verify(friendshipRepository).save(friendship);
        }

        @Test
        @DisplayName("수신자가 아닌 사용자는 수락할 수 없다")
        void givenNonAddressee_whenAccept_thenThrowsException() {
            // given
            Friendship friendship = createFriendship(FriendshipStatus.PENDING);
            when(friendshipRepository.findById(10L)).thenReturn(Optional.of(friendship));

            // when & then
            assertThatThrownBy(() -> friendshipService.acceptFriendRequest(1L, 10L))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.ACCESS_DENIED));
        }

        @Test
        @DisplayName("존재하지 않는 친구 요청은 수락할 수 없다")
        void givenNonExistentFriendship_whenAccept_thenThrowsException() {
            // given
            when(friendshipRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> friendshipService.acceptFriendRequest(2L, 999L))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.FRIENDSHIP_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("rejectFriendRequest")
    class RejectFriendRequest {

        @Test
        @DisplayName("받은 친구 요청을 거절한다")
        void givenPendingRequest_whenReject_thenChangesToRejected() {
            // given
            Friendship friendship = createFriendship(FriendshipStatus.PENDING);
            when(friendshipRepository.findById(10L)).thenReturn(Optional.of(friendship));
            when(friendshipRepository.save(any(Friendship.class))).thenReturn(friendship);

            // when
            friendshipService.rejectFriendRequest(2L, 10L);

            // then
            assertThat(friendship.isRejected()).isTrue();
        }
    }

    @Nested
    @DisplayName("cancelSentFriendRequest")
    class CancelSentFriendRequest {

        @Test
        @DisplayName("보낸 친구 요청을 취소한다")
        void givenPendingSentRequest_whenCancel_thenDeletesFriendship() {
            // given
            Friendship friendship = createFriendship(FriendshipStatus.PENDING);
            when(friendshipRepository.findById(10L)).thenReturn(Optional.of(friendship));

            // when
            friendshipService.cancelSentFriendRequest(1L, 10L);

            // then
            verify(friendshipRepository).delete(friendship);
        }

        @Test
        @DisplayName("요청자가 아닌 사용자는 취소할 수 없다")
        void givenNonRequester_whenCancel_thenThrowsException() {
            // given
            Friendship friendship = createFriendship(FriendshipStatus.PENDING);
            when(friendshipRepository.findById(10L)).thenReturn(Optional.of(friendship));

            // when & then
            assertThatThrownBy(() -> friendshipService.cancelSentFriendRequest(2L, 10L))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.ACCESS_DENIED));
        }

        @Test
        @DisplayName("PENDING이 아닌 요청은 취소할 수 없다")
        void givenAcceptedFriendship_whenCancel_thenThrowsException() {
            // given
            Friendship friendship = createFriendship(FriendshipStatus.ACCEPTED);
            when(friendshipRepository.findById(10L)).thenReturn(Optional.of(friendship));

            // when & then
            assertThatThrownBy(() -> friendshipService.cancelSentFriendRequest(1L, 10L))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.ONLY_PENDING_CAN_BE_CANCELED));
        }
    }

    @Nested
    @DisplayName("deleteFriendship")
    class DeleteFriendship {

        @Test
        @DisplayName("수락된 친구 관계를 소프트 삭제한다")
        void givenAcceptedFriendship_whenDelete_thenSoftDeletes() {
            // given
            Friendship friendship = createFriendship(FriendshipStatus.ACCEPTED);
            when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
            when(userRepository.findById(2L)).thenReturn(Optional.of(addressee));
            when(friendshipRepository.findFriendshipBetweenUsers(requester, addressee, FriendshipStatus.ACCEPTED))
                    .thenReturn(Optional.of(friendship));
            when(friendshipRepository.save(any(Friendship.class))).thenReturn(friendship);

            // when
            friendshipService.deleteFriendship(1L, 2L);

            // then
            assertThat(friendship.isDeleted()).isTrue();
        }
    }

    @Nested
    @DisplayName("blockUser")
    class BlockUser {

        @Test
        @DisplayName("사용자를 차단한다")
        void givenAcceptedFriendship_whenBlock_thenChangesToBlocked() {
            // given
            Friendship friendship = createFriendship(FriendshipStatus.ACCEPTED);
            when(friendshipRepository.findById(10L)).thenReturn(Optional.of(friendship));
            when(friendshipRepository.save(any(Friendship.class))).thenReturn(friendship);

            // when
            friendshipService.blockUser(1L, 10L);

            // then
            assertThat(friendship.isBlocked()).isTrue();
        }

        @Test
        @DisplayName("관계의 당사자가 아닌 사용자는 차단할 수 없다")
        void givenNonParticipant_whenBlock_thenThrowsException() {
            // given
            Friendship friendship = createFriendship(FriendshipStatus.ACCEPTED);
            when(friendshipRepository.findById(10L)).thenReturn(Optional.of(friendship));

            // when & then
            assertThatThrownBy(() -> friendshipService.blockUser(999L, 10L))
                    .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("reportUser")
    class ReportUser {

        @Test
        @DisplayName("기존 관계가 있으면 BLOCKED로 변경한다")
        void givenExistingRelation_whenReport_thenChangesToBlocked() {
            // given
            Friendship friendship = createFriendship(FriendshipStatus.ACCEPTED);
            when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
            when(userRepository.findById(2L)).thenReturn(Optional.of(addressee));
            when(friendshipRepository.findFriendshipBetweenUsersAnyStatus(requester, addressee))
                    .thenReturn(Optional.of(friendship));
            when(friendshipRepository.save(any(Friendship.class))).thenReturn(friendship);

            // when
            friendshipService.reportUser(1L, 2L);

            // then
            assertThat(friendship.isBlocked()).isTrue();
            verify(friendshipRepository).save(friendship);
        }

        @Test
        @DisplayName("기존 관계가 없으면 새로운 BLOCKED 관계를 생성한다")
        void givenNoExistingRelation_whenReport_thenCreatesBlockedRelation() {
            // given
            when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
            when(userRepository.findById(2L)).thenReturn(Optional.of(addressee));
            when(friendshipRepository.findFriendshipBetweenUsersAnyStatus(requester, addressee))
                    .thenReturn(Optional.empty());

            // when
            friendshipService.reportUser(1L, 2L);

            // then
            verify(friendshipRepository).save(argThat(f ->
                    f.getFriendshipStatus() == FriendshipStatus.BLOCKED));
        }
    }

    @Nested
    @DisplayName("getFriendsList")
    class GetFriendsList {

        @Test
        @DisplayName("친구 목록을 조회한다")
        void givenUserWithFriends_whenGetList_thenReturnsFriends() {
            // given
            when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
            when(friendshipRepository.findByRequesterAndFriendshipStatusOrderByCreatedAtDesc(requester, FriendshipStatus.ACCEPTED))
                    .thenReturn(List.of(createFriendship(FriendshipStatus.ACCEPTED)));
            when(friendshipRepository.findByAddresseeAndFriendshipStatusOrderByCreatedAtDesc(requester, FriendshipStatus.ACCEPTED))
                    .thenReturn(Collections.emptyList());
            when(callRepository.findLastCompletedCallBetweenUsers(anyLong(), anyLong()))
                    .thenReturn(Optional.empty());

            // when
            FriendListResponse response = friendshipService.getFriendsList(1L);

            // then
            assertThat(response).isNotNull();
        }
    }

    @Nested
    @DisplayName("getPendingFriendRequests")
    class GetPendingFriendRequests {

        @Test
        @DisplayName("받은 친구 요청 목록을 조회한다")
        void givenPendingRequests_whenGetList_thenReturnsRequests() {
            // given
            when(userRepository.findById(2L)).thenReturn(Optional.of(addressee));
            when(friendshipRepository.findByAddresseeAndFriendshipStatusOrderByCreatedAtDesc(addressee, FriendshipStatus.PENDING))
                    .thenReturn(List.of(createFriendship(FriendshipStatus.PENDING)));

            // when
            PendingFriendRequestListResponse response = friendshipService.getPendingFriendRequests(2L);

            // then
            assertThat(response).isNotNull();
        }
    }
}
