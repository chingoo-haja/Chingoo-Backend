package com.ldsilver.chingoohaja.validation;

import lombok.experimental.UtilityClass;

import java.util.regex.Pattern;

@UtilityClass
public class CallValidationConstants {

    public static final Pattern CHANNEL_NAME_PATTERN = Pattern.compile("^[\\p{ASCII}&&[^\\p{Cntrl}]]+$"); // 또는 SDK 허용 문자 리스트로 구성


    @UtilityClass
    public static class Token {
    }
}
