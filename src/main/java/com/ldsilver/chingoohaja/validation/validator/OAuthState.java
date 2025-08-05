package com.ldsilver.chingoohaja.validation.validator;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = OAuthState.OAuthStateValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface OAuthState {

    String message() default "올바르지 않은 OAuth State 형식입니다.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class OAuthStateValidator implements ConstraintValidator<OAuthState, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null || value.trim().isEmpty()) {
                return false;
            }

            // State는 URL-safe한 랜덤 문자열이어야 함
            return value.matches("^[A-Za-z0-9_-]+$") &&
                    value.length() >= 16 &&
                    value.length() <= 128;
        }
    }
}
