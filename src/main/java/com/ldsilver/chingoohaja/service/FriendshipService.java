package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.dto.friendship.response.FriendListResponse;
import com.ldsilver.chingoohaja.repository.CallRepository;
import com.ldsilver.chingoohaja.repository.FriendshipRepository;
import com.ldsilver.chingoohaja.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FriendshipService {
    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final CallRepository callRepository;

    @Transactional(readOnly = true)
    public FriendListResponse getFriendList(Long userId){
        log.debug("친구 목록 조회 시작 - userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        List<User> friends = friendshipRepository.findFriendsByUser(user);

        List<FriendListResponse.FriendItem> friendItems = friends.stream()
                .map(friend -> {
                    var lastCall = callRepository.findLastCompletedCallBetweenUsers(userId, friend.getId());

                    return FriendListResponse.FriendItem.of(
                            friend,
                            lastCall.orElse(null)
                    );
                })
                .sorted((a, b) -> {
                    if (a.lastCallAt() == null && b.lastCallAt() == null) {
                        return 0;
                    }
                    if (a.lastCallAt() == null) {
                        return -1;
                    }
                    if (b.lastCallAt() == null) {
                        return -1;
                    }
                    return b.lastCallAt().compareTo(a.lastCallAt());
                }).collect(Collectors.toList());

        log.debug("친구 목록 조회 완료 - userId: {}, friendCount: {}", userId, friendItems.size());
        return FriendListResponse.of(friendItems);
    }
}
