package com.ldsilver.chingoohaja.validation.validator;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("OAuthStateValidator 테스트")
class OAuthStateValidatorTest {

    private OAuthState.OAuthStateValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new OAuthState.OAuthStateValidator();
        context = mock(ConstraintValidatorContext.class);
    }

    @Nested
    @DisplayName("유효한 State 값")
    class ValidState {

        @Test
        @DisplayName("16자 이상 URL-safe 문자열은 유효하다")
        void givenValidState_whenValidate_thenReturnsTrue() {
            // given
            String state = "abcdefghij123456"; // 16자

            // when & then
            assertThat(validator.isValid(state, context)).isTrue();
        }

        @Test
        @DisplayName("128자 URL-safe 문자열은 유효하다")
        void givenMaxLengthState_whenValidate_thenReturnsTrue() {
            // given
            String state = "a".repeat(128);

            // when & then
            assertThat(validator.isValid(state, context)).isTrue();
        }

        @Test
        @DisplayName("언더스코어, 하이픈이 포함된 문자열은 유효하다")
        void givenStateWithUnderscoreAndHyphen_whenValidate_thenReturnsTrue() {
            // given
            String state = "abc_def-ghi_12345";

            // when & then
            assertThat(validator.isValid(state, context)).isTrue();
        }
    }

    @Nested
    @DisplayName("유효하지 않은 State 값")
    class InvalidState {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("null, 빈 문자열, 공백은 유효하지 않다")
        void givenNullOrEmpty_whenValidate_thenReturnsFalse(String state) {
            assertThat(validator.isValid(state, context)).isFalse();
        }

        @Test
        @DisplayName("16자 미만은 유효하지 않다")
        void givenTooShortState_whenValidate_thenReturnsFalse() {
            // given
            String state = "a".repeat(15);

            // when & then
            assertThat(validator.isValid(state, context)).isFalse();
        }

        @Test
        @DisplayName("128자 초과는 유효하지 않다")
        void givenTooLongState_whenValidate_thenReturnsFalse() {
            // given
            String state = "a".repeat(129);

            // when & then
            assertThat(validator.isValid(state, context)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {"abc!@#defghij12345", "state with spaces1", "state.with.dots123"})
        @DisplayName("URL-safe 문자 이외의 문자가 포함되면 유효하지 않다")
        void givenInvalidChars_whenValidate_thenReturnsFalse(String state) {
            assertThat(validator.isValid(state, context)).isFalse();
        }
    }
}
