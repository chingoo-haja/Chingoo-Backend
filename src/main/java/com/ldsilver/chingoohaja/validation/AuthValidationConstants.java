package com.ldsilver.chingoohaja.validation;

import lombok.experimental.UtilityClass;

@UtilityClass
public class AuthValidationConstants {

    @UtilityClass
    public static class Token {
        // 토큰 길이 제한
        public static final int MIN_TOKEN_LENGTH = 10;
        public static final int MAX_TOKEN_LENGTH = 2048;

        // 토큰 패턴 (Base64 URL-safe)
        public static final String TOKEN_PATTERN = "^[A-Za-z0-9._-]+$";

        // Refresh Token 에러 메시지
        public static final String REFRESH_TOKEN_REQUIRED = "리프레시 토큰은 필수입니다.";
        public static final String REFRESH_TOKEN_INVALID_LENGTH = "리프레시 토큰 길이가 올바르지 않습니다. (10-2048자)";
        public static final String REFRESH_TOKEN_INVALID_FORMAT = "리프레시 토큰 형식이 올바르지 않습니다.";

        // Access Token 에러 메시지
        public static final String ACCESS_TOKEN_REQUIRED = "액세스 토큰은 필수입니다.";
        public static final String ACCESS_TOKEN_INVALID_LENGTH = "액세스 토큰 길이가 올바르지 않습니다. (10-2048자)";
        public static final String ACCESS_TOKEN_INVALID_FORMAT = "액세스 토큰 형식이 올바르지 않습니다.";
    }

    @UtilityClass
    public static class Device {
        // 디바이스 정보 제한
        public static final int MAX_DEVICE_INFO_LENGTH = 500;

        // 에러 메시지
        public static final String DEVICE_INFO_REQUIRED = "디바이스 정보는 필수입니다.";
        public static final String DEVICE_INFO_TOO_LONG = "디바이스 정보가 너무 깁니다. (최대 500자)";
        public static final String DEVICE_INFO_INVALID_FORMAT = "올바르지 않은 디바이스 정보 형식입니다.";
    }

    @UtilityClass
    public static class OAuth {
        // Authorization Code 제한
        public static final int MIN_AUTH_CODE_LENGTH = 20;
        public static final int MAX_AUTH_CODE_LENGTH = 256;

        // State 제한
        public static final int MIN_STATE_LENGTH = 16;
        public static final int MAX_STATE_LENGTH = 128;

        // Redirect URI 제한
        public static final int MAX_REDIRECT_URI_LENGTH = 512;

        // PKCE 제한
        public static final int MIN_CODE_VERIFIER_LENGTH = 43;
        public static final int MAX_CODE_VERIFIER_LENGTH = 128;

        public static final int KAKAO_MIN_CODE_LENGTH = 10;
        public static final int KAKAO_MAX_CODE_LENGTH = 100;

        public static final int GOOGLE_MIN_CODE_LENGTH = 20;
        public static final int GOOGLE_MAX_CODE_LENGTH = 200;

        public static final int NAVER_MIN_CODE_LENGTH = 15;
        public static final int NAVER_MAX_CODE_LENGTH = 150;

        public static final int MIN_CODE_LENGTH = 10;

        // 에러 메시지
        public static final String AUTH_CODE_REQUIRED = "인가 코드는 필수입니다.";
        public static final String AUTH_CODE_INVALID_LENGTH = "인가 코드 길이가 올바르지 않습니다.";
        public static final String STATE_REQUIRED = "State 파라미터는 필수입니다.";
        public static final String STATE_TOO_LONG = "State 파라미터가 너무 깁니다. (최대 128자)";
        public static final String STATE_TOO_SHORT = "State 파라미터가 너무 짧습니다. (최소 16자)";
        public static final String REDIRECT_URI_TOO_LONG = "리다이렉트 URI가 너무 깁니다. (최대 512자)";
        public static final String CODE_VERIFIER_INVALID_LENGTH = "Code Verifier 길이가 올바르지 않습니다. (43-128자)";
    }
}
