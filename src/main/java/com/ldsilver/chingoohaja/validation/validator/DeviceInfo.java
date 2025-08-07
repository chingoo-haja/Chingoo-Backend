package com.ldsilver.chingoohaja.validation.validator;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = DeviceInfo.DeviceInfoValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface DeviceInfo {
    String message() default "올바르지 않은 디바이스 정보 형식입니다.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    boolean nullable() default true;

    class DeviceInfoValidator implements ConstraintValidator<DeviceInfo, String> {

        private boolean nullable;

        @Override
        public void initialize(DeviceInfo constraintAnnotation) {
            this.nullable = constraintAnnotation.nullable();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) {
                return nullable;
            }

            if (value.trim().isEmpty()) {
                return nullable;
            }

            // 디바이스 정보 길이 및 내용 검증
            if (value.length() > 500) {
                return false;
            }

            // 기본적인 SQL Injection, XSS 방지
            String suspicious = value.toLowerCase();
            return !suspicious.contains("<script") &&
                    !suspicious.contains("javascript:") &&
                    !suspicious.contains("'") &&
                    !suspicious.contains("\"") &&
                    !suspicious.contains(";") &&
                    !suspicious.contains("--");
        }
    }
}
