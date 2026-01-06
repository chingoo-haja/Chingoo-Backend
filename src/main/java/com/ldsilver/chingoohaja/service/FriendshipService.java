package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.friendship.Friendship;
import com.ldsilver.chingoohaja.domain.friendship.enums.FriendshipStatus;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.dto.friendship.response.FriendListResponse;
import com.ldsilver.chingoohaja.dto.friendship.response.PendingFriendRequestListResponse;
import com.ldsilver.chingoohaja.dto.friendship.response.SentFriendRequestListResponse;
import com.ldsilver.chingoohaja.repository.CallRepository;
import com.ldsilver.chingoohaja.repository.FriendshipRepository;
import com.ldsilver.chingoohaja.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FriendshipService {
    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final CallRepository callRepository;

    @Transactional(readOnly = true)
    public FriendListResponse getFriendsList(Long userId){
        log.debug("친구 목록 조회 시작 - userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        List<User> friendsAsRequester = friendshipRepository
                .findByRequesterAndFriendshipStatusOrderByCreatedAtDesc(user, FriendshipStatus.ACCEPTED)
                .stream()
                .map(Friendship::getAddressee)
                .toList();

        List<User> friendsAsAddressee = friendshipRepository
                .findByAddresseeAndFriendshipStatusOrderByCreatedAtDesc(user, FriendshipStatus.ACCEPTED)
                .stream()
                .map(Friendship::getRequester)
                .toList();

        Set<User> uniqueFriends = new HashSet<>();
        uniqueFriends.addAll(friendsAsRequester);
        uniqueFriends.addAll(friendsAsAddressee);

        List<FriendListResponse.FriendItem> friendItems = uniqueFriends.stream()
                .map(friend -> {
                    var lastCall = callRepository.findLastCompletedCallBetweenUsers(userId, friend.getId());
                    return FriendListResponse.FriendItem.of(
                            friend,
                            lastCall.orElse(null)
                    );
                })
                .sorted((a, b) -> {
                    if (a.lastCallAt() == null && b.lastCallAt() == null) return 0;
                    if (a.lastCallAt() == null) return 1;
                    if (b.lastCallAt() == null) return -1;
                    return b.lastCallAt().compareTo(a.lastCallAt());
                })
                .collect(Collectors.toList());

        log.debug("친구 목록 조회 완료 - userId: {}, friendCount: {}", userId, friendItems.size());
        return FriendListResponse.of(friendItems);
    }

    @Transactional
    public void sendFriendRequest(Long requesterId, String addresseeNickname) {
        log.debug("친구 요청 전송 - requesterId: {}, addresseeNickname: {}", requesterId, addresseeNickname);

        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        User addressee = userRepository.findByNickname(addresseeNickname)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (requesterId.equals(addressee.getId())) {
            throw new CustomException(ErrorCode.SELF_FRIENDSHIP_NOT_ALLOWED);
        }

        validateFriendshipDuplication(requester, addressee);

        Friendship friendship = Friendship.from(requester, addressee);
        friendshipRepository.save(friendship);

        log.debug("친구 요청 전송 완료 - requesterId: {}, addresseeId: {}", requesterId, addressee.getId());
    }

    @Transactional
    public void acceptFriendRequest(Long userId, Long friendshipId) {
        log.debug("친구 요청 수락 - userId: {}, friendshipId: {}", userId, friendshipId);

        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new CustomException(ErrorCode.FRIENDSHIP_NOT_FOUND));

        validateAddresseePermission(friendship, userId);

        try {
            friendship.accept();
            friendshipRepository.save(friendship);
            log.debug("친구 요청 수락 완료 - userId: {}, friendshipId: {}", userId, friendshipId);
        } catch (IllegalStateException e) {
            log.error("친구 요청 수락 실패 - 상태 전환 오류: {}", e.getMessage());
            throw new CustomException(ErrorCode.FAILED_FRIENDSHIP_REQUEST);
        }
    }

    @Transactional
    public void rejectFriendRequest(Long userId, Long friendshipId) {
        log.debug("친구 요청 거절 - userId: {}, friendshipId: {}", userId, friendshipId);

        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new CustomException(ErrorCode.FRIENDSHIP_NOT_FOUND));

        validateAddresseePermission(friendship, userId);

        try {
            friendship.reject();
            friendshipRepository.save(friendship);
            log.debug("친구 요청 거절 완료 - userId: {}, friendshipId: {}", userId, friendshipId);
        } catch (IllegalStateException e) {
            log.error("친구 요청 거절 실패 - 상태 전환 오류: {}", e.getMessage());
            throw new CustomException(ErrorCode.FAILED_FRIENDSHIP_REQUEST);
        }
    }

    @Transactional(readOnly = true)
    public PendingFriendRequestListResponse getPendingFriendRequests(Long userId) {
        log.debug("받은 친구 요청 목록 조회 시작 - userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        List<Friendship> pendingRequests = friendshipRepository
                .findByAddresseeAndFriendshipStatusOrderByCreatedAtDesc(user, FriendshipStatus.PENDING);

        List<PendingFriendRequestListResponse.PendingFriendRequestItem> requestItems =
                pendingRequests.stream()
                        .map(PendingFriendRequestListResponse.PendingFriendRequestItem::from)
                        .collect(Collectors.toList());

        log.debug("받은 친구 요청 목록 조회 완료 - userId: {}, requestCount: {}",
                userId, requestItems.size());

        return PendingFriendRequestListResponse.of(requestItems);
    }

    @Transactional(readOnly = true)
    public SentFriendRequestListResponse getSentFriendRequests(Long userId) {
        log.debug("보낸 친구 요청 목록 조회 시작 - userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        List<Friendship> sentRequests = friendshipRepository
                .findByRequesterAndFriendshipStatusOrderByCreatedAtDesc(user, FriendshipStatus.PENDING);

        List<SentFriendRequestListResponse.SentFriendRequestItem> requestItems =
                sentRequests.stream()
                        .map(SentFriendRequestListResponse.SentFriendRequestItem::from)
                        .collect(Collectors.toList());
        log.debug("보낸 친구 요청 목록 조회 완료 - userId: {}, requestCount: {}",
                userId, requestItems.size());

        return SentFriendRequestListResponse.of(requestItems);
    }

    @Transactional
    public void cancelSentFriendRequest(Long userId, Long friendshipId) {
        log.debug("보낸 친구 요청 취소 - userId: {}, friendshipId: {}", userId, friendshipId);

        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new CustomException(ErrorCode.FRIENDSHIP_NOT_FOUND));

        if (!friendship.getRequester().getId().equals(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        if (!friendship.isPending()) {
            throw new CustomException(ErrorCode.ONLY_PENDING_CAN_BE_CANCELED);
        }

        friendshipRepository.delete(friendship);

        log.debug("보낸 친구 요청 취소 완료 - userId: {}, friendshipId: {}", userId, friendshipId);
    }

    @Transactional
    public void deleteFriendship(Long userId, Long friendId) {
        log.debug("친구 삭제 (소프트 삭제) - userId: {}, friendId: {}", userId, friendId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        User friend = userRepository.findById(friendId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Friendship friendship = friendshipRepository.findFriendshipBetweenUsers(user, friend, FriendshipStatus.ACCEPTED)
                .orElseThrow(() -> new CustomException(ErrorCode.FRIENDSHIP_NOT_FOUND));

        validateDeletePermission(friendship, userId);

        try {
            friendship.delete();
            friendshipRepository.save(friendship);
            log.debug("친구 삭제 완료 (소프트 삭제) - userId: {}, friendId: {}, friendshipId: {}",
                    userId, friendId, friendship.getId());
        } catch (IllegalStateException e) {
            log.error("친구 삭제 실패 - 상태 전환 오류: {}", e.getMessage());
            throw new CustomException(ErrorCode.FAILED_FRIENDSHIP_REQUEST);
        }
    }

    @Transactional
    public void blockUser(Long userId, Long friendshipId) {
        log.debug("사용자 차단 - userId: {}, friendshipId: {}", userId, friendshipId);

        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new CustomException(ErrorCode.FRIENDSHIP_NOT_FOUND));

        validateBlockPermission(friendship, userId);

        try {
            friendship.block();
            friendshipRepository.save(friendship);
            log.debug("사용자 차단 완료 - userId: {}, friendshipId: {}", userId, friendshipId);
        } catch (IllegalStateException e) {
            log.error("사용자 차단 실패 - 상태 전환 오류: {}", e.getMessage());
            throw new CustomException(ErrorCode.FAILED_FRIENDSHIP_REQUEST);
        }
    }

    @Transactional
    public void reportUser(Long reporterId, Long reportedUserId) {
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        User reported = userRepository.findById(reportedUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Optional<Friendship> existingFriendship = friendshipRepository
                .findFriendshipBetweenUsersAnyStatus(reporter, reported);

        if (existingFriendship.isPresent()) {
            Friendship friendship = existingFriendship.get();
            friendship.block(); // 기존 block() 메서드 사용
            friendshipRepository.save(friendship);
            log.info("기존 관계를 BLOCKED로 변경 - reporterId: {}, reportedId: {}",
                    reporterId, reportedUserId);
        } else {
            // 새로운 차단 관계 생성
            Friendship newBlock = Friendship.of(reporter, reported, FriendshipStatus.BLOCKED);
            friendshipRepository.save(newBlock);
            log.info("새로운 BLOCKED 관계 생성 - reporterId: {}, reportedId: {}",
                    reporterId, reportedUserId);
        }
    }


    // ========== Private 권한 검증 메서드 (Service 책임) ==========

    private void validateFriendshipDuplication(User requester,User addressee){
        // 1. 이미 친구인 경우
        Optional<Friendship> existingFriendship =
                friendshipRepository.findFriendshipBetweenUsers(requester, addressee, FriendshipStatus.ACCEPTED);

        if (existingFriendship.isPresent()) {
            log.debug("이미 친구 관계 - requesterId: {}, addresseeId: {}",
                    requester.getId(), addressee.getId());
            throw new CustomException(ErrorCode.FRIENDSHIP_ALREADY_EXISTS);
        }

        // 2. PENDING 상태 확인
        Optional<Friendship> pendingFriendship =
                friendshipRepository.findFriendshipBetweenUsers(requester, addressee, FriendshipStatus.PENDING);

        if (pendingFriendship.isPresent()) {
            Friendship friendship = pendingFriendship.get();

            // 2-1. 내가 이미 요청을 보낸 경우
            if (friendship.getRequester().getId().equals(requester.getId())) {
                log.debug("이미 친구 요청을 보냄 - requesterId: {}, addresseeId: {}",
                        requester.getId(), addressee.getId());
                throw new CustomException(ErrorCode.FRIENDSHIP_REQUEST_ALREADY_SENT);
            }

            // 2-2. 상대방이 나에게 요청을 보낸 경우
            if (friendship.getAddressee().getId().equals(requester.getId())) {
                log.debug("상대방으로부터 이미 친구 요청을 받음 - requesterId: {}, addresseeId: {}",
                        requester.getId(), addressee.getId());
                throw new CustomException(ErrorCode.FRIENDSHIP_REQUEST_ALREADY_RECEIVED);
            }
        }
    }

    private void validateAddresseePermission(Friendship friendship, Long userId) {
        if (!friendship.getAddressee().getId().equals(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        if (!friendship.isPending()) {
            throw new CustomException(ErrorCode.FAILED_FRIENDSHIP_PERMISSION);
        }
    }

    private void validateDeletePermission(Friendship friendship, Long userId) {
        boolean isRequester = friendship.getRequester().getId().equals(userId);
        boolean isAddressee = friendship.getAddressee().getId().equals(userId);

        if (!isRequester && !isAddressee) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        if (!friendship.isAccepted()) {
            throw new CustomException(ErrorCode.FRIENDSHIP_NOT_FOUND);
        }
    }

    private void validateBlockPermission(Friendship friendship, Long userId) {
        boolean isRequester = friendship.getRequester().getId().equals(userId);
        boolean isAddressee = friendship.getAddressee().getId().equals(userId);

        if (!isRequester && !isAddressee) {
            throw new CustomException(ErrorCode.FAILED_FRIENDSHIP_PERMISSION);
        }
    }

}
