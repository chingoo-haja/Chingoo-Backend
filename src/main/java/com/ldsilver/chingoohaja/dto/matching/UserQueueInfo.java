package com.ldsilver.chingoohaja.dto.matching;

import java.util.Optional;

public record UserQueueInfo(
        Long categoryId,
        String queueId,
        Long timestamp
) {
    public boolean isExpired(long currentTimestamp, int ttlSeconds) {
        return Optional.ofNullable(timestamp)
                .map(ts -> (currentTimestamp - ts) > (ttlSeconds * 1000L))
                .orElse(true);
    }

    public long getWaitTimeSeconds(long currentTimestamp) {
        return Optional.ofNullable(timestamp)
                .map(ts -> Math.max(0, (currentTimestamp - ts) / 1000L))
                .orElse(0L);
    }
}
