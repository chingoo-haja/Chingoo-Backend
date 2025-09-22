package com.ldsilver.chingoohaja.event;

import com.ldsilver.chingoohaja.domain.user.User;

import java.util.List;


public record MatchingSuccessEvent(
        Long callId,
        Long categoryId,
        List<Long> userIds,
        User user1,
        User user2) {
}
