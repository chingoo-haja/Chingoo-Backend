package com.ldsilver.chingoohaja.validation.validator;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("DeviceInfoValidator 테스트")
class DeviceInfoValidatorTest {

    private DeviceInfo.DeviceInfoValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new DeviceInfo.DeviceInfoValidator();
        context = mock(ConstraintValidatorContext.class);
    }

    @Nested
    @DisplayName("nullable = true (기본값)")
    class NullableTrue {

        @BeforeEach
        void setUp() {
            DeviceInfo annotation = mock(DeviceInfo.class);
            org.mockito.Mockito.when(annotation.nullable()).thenReturn(true);
            validator.initialize(annotation);
        }

        @Test
        @DisplayName("null 입력은 유효하다")
        void givenNull_whenValidate_thenReturnsTrue() {
            assertThat(validator.isValid(null, context)).isTrue();
        }

        @Test
        @DisplayName("빈 문자열은 유효하다")
        void givenEmpty_whenValidate_thenReturnsTrue() {
            assertThat(validator.isValid("", context)).isTrue();
        }

        @Test
        @DisplayName("정상 디바이스 정보는 유효하다")
        void givenValidDeviceInfo_whenValidate_thenReturnsTrue() {
            assertThat(validator.isValid("iPhone 15 Pro, iOS 17.0", context)).isTrue();
        }

        @Test
        @DisplayName("500자 초과 입력은 유효하지 않다")
        void givenTooLongInput_whenValidate_thenReturnsFalse() {
            // given
            String longInput = "a".repeat(501);

            // when & then
            assertThat(validator.isValid(longInput, context)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "<script>alert('xss')</script>",
                "javascript:void(0)",
                "device'; DROP TABLE users;--",
                "device\"info",
                "info;malicious"
        })
        @DisplayName("악의적 입력은 유효하지 않다")
        void givenMaliciousInput_whenValidate_thenReturnsFalse(String input) {
            assertThat(validator.isValid(input, context)).isFalse();
        }
    }

    @Nested
    @DisplayName("nullable = false")
    class NullableFalse {

        @BeforeEach
        void setUp() {
            DeviceInfo annotation = mock(DeviceInfo.class);
            org.mockito.Mockito.when(annotation.nullable()).thenReturn(false);
            validator.initialize(annotation);
        }

        @Test
        @DisplayName("null 입력은 유효하지 않다")
        void givenNull_whenValidate_thenReturnsFalse() {
            assertThat(validator.isValid(null, context)).isFalse();
        }

        @Test
        @DisplayName("빈 문자열은 유효하지 않다")
        void givenEmpty_whenValidate_thenReturnsFalse() {
            assertThat(validator.isValid("  ", context)).isFalse();
        }
    }
}
