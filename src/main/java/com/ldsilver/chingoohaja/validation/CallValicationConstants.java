package com.ldsilver.chingoohaja.validation;

import lombok.experimental.UtilityClass;

import java.util.regex.Pattern;

@UtilityClass
public class CallValicationConstants {

    public static final Pattern CHANNEL_NAME_PATTERN = Pattern.compile("^[\\p{ASCII}&&[^\\p{Cntrl}]]+$"); // 또는 SDK 허용 문자 리스트로 구성


    @UtilityClass
    public static class Token {
        public static final String UID_NOT_MINUS = "uid는 음수일 수 없습니다.";
        public static final String ROLE_REQUIRED = "role은 필수입니다."; //60초
        public static final String INVALID_EXPIRED_TIME = "만료시간(초)는 0보다 커야 합니다.";
    }
}
