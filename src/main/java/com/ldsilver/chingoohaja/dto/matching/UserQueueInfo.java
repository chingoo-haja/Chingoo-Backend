package com.ldsilver.chingoohaja.dto.matching;

public record UserQueueInfo(
        Long categoryId,
        String queueId,
        Long timestamp
) {
    public boolean isExpired(long currentTimestamp, int ttlSeconds) {
        return (currentTimestamp - timestamp) > (ttlSeconds * 1000L);
    }

    public long getWaitTimeSeconds(long currentTimestamp) {
        return Math.max(0, (currentTimestamp - timestamp) / 1000L);
    }
}
