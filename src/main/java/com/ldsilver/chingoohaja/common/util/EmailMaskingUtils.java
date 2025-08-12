package com.ldsilver.chingoohaja.common.util;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class EmailMaskingUtils {
    private static final String DEFAULT_MASK = "***@***.***";
    private static final String SIMPLE_MASK = "***";
    private static final String AT_SYMBOL = "@";
    private static final String MASK_PATTERN = "***";

    private EmailMaskingUtils() {
        throw new AssertionError("EmailMaskingUtils는 인스턴스화할 수 없습니다.");
    }

    public static String maskEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            log.debug("이메일이 null이거나 빈 문자열입니다.");
            return DEFAULT_MASK;
        }

        String trimmedEmail = email.trim();

        if (!trimmedEmail.contains(AT_SYMBOL)) {
            log.debug("이메일에 @기호가 없습니다.");
            return DEFAULT_MASK;
        }

        String[] parts = trimmedEmail.split(AT_SYMBOL);

        if (parts.length != 2) {
            log.debug("이메일 형식이 잘못되었습니다.");
            return DEFAULT_MASK;
        }

        String localPart = parts[0];
        String domainPart = parts[1];

        if (localPart.isEmpty() || domainPart.isEmpty()) {
            log.debug("이메일의 로컬 부분 또는 도메인 부분이 비어있습니다.");
            return DEFAULT_MASK;
        }

        if (!domainPart.contains(".")) {
            log.debug("도메인 부분에 점(.)이 없습니다.");
            return DEFAULT_MASK;
        }

        return maskLocalPart(localPart) + AT_SYMBOL + domainPart;
    }


    // 로그용 간단한 이메일 마스킹
    public static String maskEmailForLog(String email) {
        if (email == null || email.trim().isEmpty() || !email.contains(AT_SYMBOL)) {
            return SIMPLE_MASK;
        }

        String[] parts = email.trim().split(AT_SYMBOL);
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            return SIMPLE_MASK;
        }

        String localPart = parts[0];
        String domainPart = parts[1];

        return maskLocalPart(localPart) + AT_SYMBOL + maskDomainPart(domainPart);
    }


    private static String maskLocalPart(String localPart) {
        if (localPart == null || localPart.isEmpty()) {
            return MASK_PATTERN;
        }

        int length = localPart.length();

        if (length <= 2) {
            return MASK_PATTERN;
        } else if (length <= 4) {
            return localPart.substring(0, 1) + MASK_PATTERN;
        } else {
            return localPart.substring(0, 2) + MASK_PATTERN;
        }
    }

    private static String maskDomainPart(String domainPart) {
        if (domainPart == null || domainPart.isEmpty()) {
            return MASK_PATTERN;
        }

        int dotIndex = domainPart.lastIndexOf('.');
        if (dotIndex > 0) {
            String domain = domainPart.substring(0, dotIndex);
            String extension = domainPart.substring(dotIndex);
            return domain.charAt(0) + MASK_PATTERN + extension;
        } else {
            return MASK_PATTERN + ".***";
        }
    }

    public static boolean isValidEmailFormat(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }

        String trimmedEmail = email.trim();

        // 기본적인 형식 검증
        if (!trimmedEmail.contains(AT_SYMBOL)) {
            return false;
        }

        String[] parts = trimmedEmail.split(AT_SYMBOL);
        if (parts.length != 2) {
            return false;
        }

        String localPart = parts[0];
        String domainPart = parts[1];

        return !localPart.isEmpty() &&
                !domainPart.isEmpty() &&
                domainPart.contains(".");
    }
}
