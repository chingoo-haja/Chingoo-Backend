package com.ldsilver.chingoohaja.validation;

import lombok.experimental.UtilityClass;

@UtilityClass
public class UserValidationConstants {

    @UtilityClass
    public static class Nickname {
        // 닉네임 제한
        public static final int MIN_LENGTH = 2;
        public static final int MAX_LENGTH = 20;

        // 닉네임 패턴 (한글, 영문, 숫자)
        public static final String PATTERN = "^[가-힣a-zA-Z0-9]+$";

        // 에러 메시지
        public static final String REQUIRED = "닉네임은 필수입니다.";
        public static final String INVALID_LENGTH = "닉네임은 2-20자여야 합니다.";
        public static final String INVALID_FORMAT = "닉네임은 한글, 영문, 숫자만 사용 가능합니다.";
        public static final String DUPLICATE = "이미 사용 중인 닉네임입니다.";
    }

    @UtilityClass
    public static class Email {
        // 이메일 제한
        public static final int MAX_LENGTH = 100;

        // 이메일 패턴
        public static final String PATTERN = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";

        // 에러 메시지
        public static final String REQUIRED = "이메일은 필수입니다.";
        public static final String INVALID_FORMAT = "올바른 이메일 형식이 아닙니다.";
        public static final String TOO_LONG = "이메일이 너무 깁니다. (최대 100자)";
        public static final String DUPLICATE = "이미 사용 중인 이메일입니다.";
    }

    @UtilityClass
    public static class RealName {
        // 실명 제한
        public static final int MIN_LENGTH = 2;
        public static final int MAX_LENGTH = 50;

        // 실명 패턴 (한글, 영문, 공백 허용)
        public static final String PATTERN = "^[가-힣a-zA-Z\\s]+$";

        // 에러 메시지
        public static final String REQUIRED = "실명은 필수입니다.";
        public static final String INVALID_LENGTH = "실명은 2-50자여야 합니다.";
        public static final String INVALID_FORMAT = "실명은 한글, 영문, 공백만 사용 가능합니다.";
    }

    @UtilityClass
    public static class Birth {
        public static final String LEAST_DOB = "생년월일은 현재 날짜보다 이전이여야 합니다.";
    }
}
