package com.ldsilver.chingoohaja.validation.validator;

import com.ldsilver.chingoohaja.domain.common.enums.Provider;
import com.ldsilver.chingoohaja.validation.AuthValidationConstants;
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
            return code.length() >= AuthValidationConstants.OAuth.KAKAO_MIN_CODE_LENGTH &&
                    code.length() <= AuthValidationConstants.OAuth.KAKAO_MAX_CODE_LENGTH &&
                    code.matches("^[A-Za-z0-9_-]+$");
        }

        private boolean validateGoogleAuthCode(String code) {
            return code.length() >= AuthValidationConstants.OAuth.GOOGLE_MIN_CODE_LENGTH &&
                    code.length() <= AuthValidationConstants.OAuth.GOOGLE_MAX_CODE_LENGTH &&
                    code.matches("^[A-Za-z0-9/_-]+$");
        }

        private boolean validateNaverAuthCode(String code) {
            return code.length() >= AuthValidationConstants.OAuth.NAVER_MIN_CODE_LENGTH &&
                    code.length() <= AuthValidationConstants.OAuth.NAVER_MAX_CODE_LENGTH &&
                    code.matches("^[A-Za-z0-9_-]+$");
        }

        private boolean validateGenericAuthCode(String code) {
            // 일반적인 Authorization Code 형식 검증
            return code.length() >= AuthValidationConstants.OAuth.MIN_AUTH_CODE_LENGTH &&
                    code.length() <= AuthValidationConstants.OAuth.MAX_AUTH_CODE_LENGTH &&
                    code.matches("^[A-Za-z0-9._/-]+$");
        }
    }

}
