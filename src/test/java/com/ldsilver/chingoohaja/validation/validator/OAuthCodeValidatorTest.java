package com.ldsilver.chingoohaja.validation.validator;

import com.ldsilver.chingoohaja.domain.common.enums.Provider;
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
import static org.mockito.Mockito.when;

@DisplayName("OAuthCodeValidator 테스트")
class OAuthCodeValidatorTest {

    private OAuthCode.OAuthCodeValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new OAuthCode.OAuthCodeValidator();
        context = mock(ConstraintValidatorContext.class);
    }

    private void initializeValidator(Provider provider) {
        OAuthCode annotation = mock(OAuthCode.class);
        when(annotation.provider()).thenReturn(provider);
        validator.initialize(annotation);
    }

    @Nested
    @DisplayName("공통 검증")
    class CommonValidation {

        @BeforeEach
        void setUp() {
            initializeValidator(Provider.ANY);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("null, 빈 문자열, 공백은 유효하지 않다")
        void givenNullOrEmpty_whenValidate_thenReturnsFalse(String code) {
            assertThat(validator.isValid(code, context)).isFalse();
        }
    }

    @Nested
    @DisplayName("Provider.KAKAO")
    class KakaoProvider {

        @BeforeEach
        void setUp() {
            initializeValidator(Provider.KAKAO);
        }

        @Test
        @DisplayName("유효한 카카오 인가 코드는 통과한다")
        void givenValidKakaoCode_whenValidate_thenReturnsTrue() {
            // given - 카카오 인가 코드: 10~100자, [A-Za-z0-9_-]
            String code = "abcdefghij"; // 10자

            // when & then
            assertThat(validator.isValid(code, context)).isTrue();
        }

        @Test
        @DisplayName("10자 미만 코드는 유효하지 않다")
        void givenTooShortCode_whenValidate_thenReturnsFalse() {
            // given
            String code = "abc"; // 3자

            // when & then
            assertThat(validator.isValid(code, context)).isFalse();
        }

        @Test
        @DisplayName("100자 초과 코드는 유효하지 않다")
        void givenTooLongCode_whenValidate_thenReturnsFalse() {
            // given
            String code = "a".repeat(101);

            // when & then
            assertThat(validator.isValid(code, context)).isFalse();
        }
    }

    @Nested
    @DisplayName("Provider.GOOGLE")
    class GoogleProvider {

        @BeforeEach
        void setUp() {
            initializeValidator(Provider.GOOGLE);
        }

        @Test
        @DisplayName("유효한 구글 인가 코드는 통과한다")
        void givenValidGoogleCode_whenValidate_thenReturnsTrue() {
            // given - 구글 인가 코드: 20~200자, [A-Za-z0-9/_-]
            String code = "4/0AX4XfWh" + "a".repeat(12); // 20자

            // when & then
            assertThat(validator.isValid(code, context)).isTrue();
        }

        @Test
        @DisplayName("20자 미만 코드는 유효하지 않다")
        void givenTooShortCode_whenValidate_thenReturnsFalse() {
            // given
            String code = "a".repeat(19);

            // when & then
            assertThat(validator.isValid(code, context)).isFalse();
        }

        @Test
        @DisplayName("슬래시(/)가 포함된 구글 코드는 유효하다")
        void givenCodeWithSlash_whenValidate_thenReturnsTrue() {
            // given
            String code = "4/0AX4XfWh_test-code12";

            // when & then
            assertThat(validator.isValid(code, context)).isTrue();
        }
    }

    @Nested
    @DisplayName("Provider.NAVER")
    class NaverProvider {

        @BeforeEach
        void setUp() {
            initializeValidator(Provider.NAVER);
        }

        @Test
        @DisplayName("유효한 네이버 인가 코드는 통과한다")
        void givenValidNaverCode_whenValidate_thenReturnsTrue() {
            // given - 네이버 인가 코드: 15~150자
            String code = "a".repeat(15);

            // when & then
            assertThat(validator.isValid(code, context)).isTrue();
        }

        @Test
        @DisplayName("15자 미만 코드는 유효하지 않다")
        void givenTooShortCode_whenValidate_thenReturnsFalse() {
            // given
            String code = "a".repeat(14);

            // when & then
            assertThat(validator.isValid(code, context)).isFalse();
        }
    }

    @Nested
    @DisplayName("Provider.ANY")
    class AnyProvider {

        @BeforeEach
        void setUp() {
            initializeValidator(Provider.ANY);
        }

        @Test
        @DisplayName("20~1000자 범위의 일반 코드는 통과한다")
        void givenValidGenericCode_whenValidate_thenReturnsTrue() {
            // given
            String code = "a".repeat(20);

            // when & then
            assertThat(validator.isValid(code, context)).isTrue();
        }

        @Test
        @DisplayName("허용되지 않는 특수문자가 포함되면 유효하지 않다")
        void givenCodeWithInvalidChars_whenValidate_thenReturnsFalse() {
            // given
            String code = "a".repeat(19) + "!@#$%";

            // when & then
            assertThat(validator.isValid(code, context)).isFalse();
        }
    }
}
