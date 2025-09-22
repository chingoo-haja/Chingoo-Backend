package com.ldsilver.chingoohaja.event;

import com.ldsilver.chingoohaja.domain.user.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class MatchingSuccessEvent {
    private final Long callId;
    private final Long categoryId;
    private final List<Long> userIds;
    private final User user1;
    private final User user2;
}
