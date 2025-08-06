package com.ldsilver.chingoohaja.validation.validator;

import com.ldsilver.chingoohaja.domain.common.enums.Provider;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = OAuthCode.OAuthAuthCodeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface OAuthCode {
    String message() default "올바르지 않은 Authorization Code 형식입니다.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    Provider provider() default Provider.ANY;

    class OAuthAuthCodeValidator implements ConstraintValidator<OAuthCode, String> {

        private Provider provider;

        @Override
        public void initialize(OAuthCode constraintAnnotation) {
            this.provider = constraintAnnotation.provider();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null || value.trim().isEmpty()) {
                return false;
            }

            return switch (provider) {
                case KAKAO -> validateKakaoAuthCode(value);
                case GOOGLE -> validateGoogleAuthCode(value);
                case NAVER -> validateNaverAuthCode(value);
                case ANY -> validateGenericAuthCode(value);
            };
        }

        private boolean validateKakaoAuthCode(String code) {
            // 카카오 Authorization Code 형식 검증
            return code != null && !code.isEmpty();
        }

        private boolean validateGoogleAuthCode(String code) {
            // 구글 Authorization Code 형식 검증
            return code != null && !code.isEmpty();
        }

        private boolean validateNaverAuthCode(String code) {
            // 네이버 Authorization Code 형식 검증
            return code != null && !code.isEmpty();
        }

        private boolean validateGenericAuthCode(String code) {
            // 일반적인 Authorization Code 형식 검증
            return code.length() >= 20 &&
                    code.length() <= 256 &&
                    code.matches("^[A-Za-z0-9._/-]+$");
        }
    }

}
