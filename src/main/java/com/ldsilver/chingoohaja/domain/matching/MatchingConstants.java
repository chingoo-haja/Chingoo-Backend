package com.ldsilver.chingoohaja.domain.matching;

import lombok.experimental.UtilityClass;

@UtilityClass
public class MatchingConstants {

    // 대기 시간 관련
    @UtilityClass
    public static class WaitTime {
        public static final int ESTIMATED_WAIT_TIME_PER_PERSON = 30; // 초
        public static final int MAX_ESTIMATED_WAIT_TIME = 600; // 10분
        public static final int MIN_ESTIMATED_WAIT_TIME = 0;
    }

    // 큐 관련
    @UtilityClass
    public static class Queue {
        public static final long DEFAULT_TTL_SECONDS = 600L; // 10분
        public static final int DEFAULT_MATCH_COUNT = 2;
        public static final int MAX_QUEUE_SIZE = 1000;
    }

    // 스케줄러 관련
    @UtilityClass
    public static class Scheduler {
        public static final long DEFAULT_FIXED_DELAY = 5000L; // 5초
        public static final long DEFAULT_CLEANUP_DELAY = 60000L; // 1분
        public static final int MAX_RETRY_ATTEMPTS = 3;
    }
}
