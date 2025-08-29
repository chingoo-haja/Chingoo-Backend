package com.ldsilver.chingoohaja.validation;

import lombok.experimental.UtilityClass;

@UtilityClass
public class MatchingValidatoinConstants {

    @UtilityClass
    public static class Scheduler {
        public static final int DEFAULT_MATCHING_DELAY = 5000;
        public static final int DEFAULT_CLEANUP_DELAY = 6000;
        public static final int DEFAULT_TTL_SECONDS = 600;
    }
}
