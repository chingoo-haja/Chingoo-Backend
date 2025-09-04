package com.ldsilver.chingoohaja.validation;

import lombok.experimental.UtilityClass;

@UtilityClass
public class MatchingValidationConstants {

    @UtilityClass
    public static class Scheduler {
        public static final int DEFAULT_MATCHING_DELAY = 5000;
        public static final int DEFAULT_CLEANUP_DELAY = 60000; //60초
        public static final int DEFAULT_EXPIRED_TIME = 600;
    }

    @UtilityClass
    public static class Queue {
        public static final long DEFAULT_TTL_SECONDS = 600; // 10분
        public static final int LOCK_TTL_SECONDS = 30;      // 락 TTL 30초
        public static final double RANDOM_MATCH_RATIO = 0.8;// 랜덤매칭 80%
    }

    @UtilityClass
    public static class WaitTime {
        public static final int ESTIMATED_WAIT_TIME_PER_PERSON = 30; // 초
        public static final int MAX_ESTIMATED_WAIT_TIME = 600; // 10분
        public static final int MIN_ESTIMATED_WAIT_TIME = 0;
    }
}
