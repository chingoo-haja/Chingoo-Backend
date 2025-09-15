package com.ldsilver.chingoohaja.validation;

import lombok.experimental.UtilityClass;

import java.util.regex.Pattern;

@UtilityClass
public class CallValidationConstants {

    public static final int CHANNEL_NAME_MAX_BYTES = 64; // UTF-8 기준
    public static final String CHANNEL_NAME_REGEX = "^[\\p{ASCII}&&[^\\p{Cntrl}]]+$";
    public static final Pattern CHANNEL_NAME_PATTERN = Pattern.compile(CHANNEL_NAME_REGEX);// 또는 SDK 허용 문자 리스트로 구성
    public static final int DEFAULT_TTL_SECONDS = 3600; // 1시간
    public static final String DEFAULT_ROLE = "PUBLISHER";
    public final long AGORA_MAX_UID = 4_294_967_295L;
}
