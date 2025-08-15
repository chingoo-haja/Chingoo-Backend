package com.ldsilver.chingoohaja.validation;

import lombok.experimental.UtilityClass;

@UtilityClass
public class CommonValidationConstants {

    public static final int MAX_PROFILE_IMAGE_URL_LENGTH = 2048;


    @UtilityClass
    public static class Common {
        // 공통 에러 메시지
        public static final String REQUIRED = "필수 입력 항목입니다.";
        public static final String INVALID_FORMAT = "올바르지 않은 형식입니다.";
        public static final String TOO_LONG = "입력 값이 너무 깁니다.";
        public static final String TOO_SHORT = "입력 값이 너무 짧습니다.";
        public static final String NOT_ALLOWED = "허용되지 않은 값입니다.";
        public static final String DUPLICATE = "이미 존재하는 값입니다.";
    }

    @UtilityClass
    public static class Id {
        // ID 제한
        public static final long MIN_VALUE = 1L;
        public static final long MAX_VALUE = Long.MAX_VALUE;

        // 에러 메시지
        public static final String INVALID_ID = "올바르지 않은 ID입니다.";
        public static final String REQUIRED = "ID는 필수입니다.";
        public static final String OUT_OF_RANGE = "ID 값이 허용 범위를 벗어났습니다.";
    }

    @UtilityClass
    public static class Date {
        // 날짜 패턴
        public static final String DATE_PATTERN = "yyyy-MM-dd";
        public static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

        // 에러 메시지
        public static final String INVALID_DATE = "올바르지 않은 날짜 형식입니다.";
        public static final String FUTURE_DATE_NOT_ALLOWED = "미래 날짜는 허용되지 않습니다.";
        public static final String PAST_DATE_NOT_ALLOWED = "과거 날짜는 허용되지 않습니다.";
    }

    @UtilityClass
    public static class Paging {
        // 페이징 제한
        public static final int MIN_PAGE = 0;
        public static final int MAX_PAGE = Integer.MAX_VALUE;
        public static final int MIN_SIZE = 1;
        public static final int MAX_SIZE = 1000;
        public static final int DEFAULT_SIZE = 20;

        // 에러 메시지
        public static final String INVALID_PAGE = "페이지 번호가 올바르지 않습니다.";
        public static final String INVALID_SIZE = "페이지 크기가 올바르지 않습니다. (1-1000)";
    }


}
