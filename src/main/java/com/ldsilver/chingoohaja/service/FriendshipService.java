package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.friendship.Friendship;
import com.ldsilver.chingoohaja.domain.friendship.enums.FriendshipStatus;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.dto.friendship.response.FriendListResponse;
import com.ldsilver.chingoohaja.repository.CallRepository;
import com.ldsilver.chingoohaja.repository.FriendshipRepository;
import com.ldsilver.chingoohaja.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
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

        // 친구 관계 조회 (ACCEPTED 상태만)
        // 내가 요청한 경우
        List<User> friendsAsRequester = friendshipRepository
                .findByRequesterAndFriendshipStatusOrderByCreatedAtDesc(user, FriendshipStatus.ACCEPTED)
                .stream()
                .map(Friendship::getAddressee)
                .toList();

        // 상대방이 요청한 경우
        List<User> friendsAsAddressee = friendshipRepository
                .findByAddresseeAndFriendshipStatusOrderByCreatedAtDesc(user, FriendshipStatus.ACCEPTED)
                .stream()
                .map(Friendship::getRequester)
                .toList();

        // 두 리스트 합치기 (중복 제거를 위해 Set 사용)
        Set<User> uniqueFriends = new HashSet<>();
        uniqueFriends.addAll(friendsAsRequester);
        uniqueFriends.addAll(friendsAsAddressee);

        // 친구별 마지막 통화 정보와 함께 DTO 생성
        List<FriendListResponse.FriendItem> friendItems = uniqueFriends.stream()
                .map(friend -> {
                    // 해당 친구와의 마지막 완료된 통화 조회
                    var lastCall = callRepository.findLastCompletedCallBetweenUsers(userId, friend.getId());

                    return FriendListResponse.FriendItem.of(
                            friend,
                            lastCall.orElse(null)
                    );
                })
                .sorted((a, b) -> {
                    // 마지막 통화 시간 기준 내림차순 정렬 (최근 통화한 친구가 위로)
                    if (a.lastCallAt() == null && b.lastCallAt() == null) {
                        return 0;
                    }
                    if (a.lastCallAt() == null) {
                        return 1;
                    }
                    if (b.lastCallAt() == null) {
                        return -1;
                    }
                    return b.lastCallAt().compareTo(a.lastCallAt());
                })
                .collect(Collectors.toList());

        log.debug("친구 목록 조회 완료 - userId: {}, friendCount: {}", userId, friendItems.size());

        return FriendListResponse.of(friendItems);
    }
}
