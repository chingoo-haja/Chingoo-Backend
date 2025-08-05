package com.ldsilver.chingoohaja.validation.validator;

import com.ldsilver.chingoohaja.domain.common.enums.TokenType;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = JwtToken.JwtTokenValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface JwtToken {

    String message() default "올바르지 않은 JWT 토큰 형식입니다.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    TokenType tokenType() default TokenType.ANY;

    boolean nullable() default false;

    class JwtTokenValidator implements ConstraintValidator<JwtToken, String> {

        private TokenType tokenType;
        private boolean nullable;

        @Override
        public void initialize(JwtToken constraintAnnotation) {
            this.tokenType = constraintAnnotation.tokenType();
            this.nullable = constraintAnnotation.nullable();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) {
                return nullable;
            }

            if (value.trim().isEmpty()) {
                return false;
            }

            String[] parts = value.split("\\.");
            if (parts.length != 3) {
                return false;
            }

            for (String part : parts) {
                if (!isValidBase64(part)) {
                    return false;
                }
            }

            return switch (tokenType) {
                case ACCESS -> validateAccessTokenFormat(value);
                case REFRESH -> validateRefreshTokenFormat(value);
                case ANY -> true;
            };

        }

        private boolean isValidBase64(String str) {
            try {
                return str.matches("^[A-Za-z0-9+/]*={0,2}$") ||
                        str.matches("^[A-Za-z0-9_-]*$"); // URL-safe Base64
            } catch (Exception e) {
                return false;
            }
        }

        private boolean validateAccessTokenFormat(String value) {
            // Access Token 길이 검증 (일반적으로 더 짧음)
            return value.length() >= 100 && value.length() <= 1024;
        }

        private boolean validateRefreshTokenFormat(String value) {
            // Refresh Token 길이 검증 (일반적으로 더 길 수 있음)
            return value.length() >= 100 && value.length() <= 2048;
        }
    }
}
