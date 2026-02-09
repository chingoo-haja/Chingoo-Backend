package com.ldsilver.chingoohaja.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EmailMaskingUtils 테스트")
class EmailMaskingUtilsTest {

    private static final String DEFAULT_MASK = "***@***.***";
    private static final String SIMPLE_MASK = "***";

    @Nested
    @DisplayName("maskEmail")
    class MaskEmail {

        @Test
        @DisplayName("일반적인 이메일을 마스킹한다")
        void givenValidEmail_whenMask_thenMasksLocalPart() {
            // given
            String email = "testuser@gmail.com";

            // when
            String result = EmailMaskingUtils.maskEmail(email);

            // then
            assertThat(result).isEqualTo("te***@gmail.com");
        }

        @Test
        @DisplayName("짧은 로컬파트(2자 이하)는 전체를 마스킹한다")
        void givenShortLocalPart_whenMask_thenMasksEntirely() {
            // given
            String email = "ab@gmail.com";

            // when
            String result = EmailMaskingUtils.maskEmail(email);

            // then
            assertThat(result).isEqualTo("***@gmail.com");
        }

        @Test
        @DisplayName("중간 로컬파트(3~4자)는 첫 글자만 남기고 마스킹한다")
        void givenMediumLocalPart_whenMask_thenKeepsFirstChar() {
            // given
            String email = "test@gmail.com";

            // when
            String result = EmailMaskingUtils.maskEmail(email);

            // then
            assertThat(result).isEqualTo("t***@gmail.com");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "\t"})
        @DisplayName("null, 빈 문자열, 공백만 있는 입력은 기본 마스크를 반환한다")
        void givenNullOrEmpty_whenMask_thenReturnsDefaultMask(String email) {
            // when
            String result = EmailMaskingUtils.maskEmail(email);

            // then
            assertThat(result).isEqualTo(DEFAULT_MASK);
        }

        @Test
        @DisplayName("@가 없는 문자열은 기본 마스크를 반환한다")
        void givenNoAtSymbol_whenMask_thenReturnsDefaultMask() {
            // when
            String result = EmailMaskingUtils.maskEmail("notanemail");

            // then
            assertThat(result).isEqualTo(DEFAULT_MASK);
        }

        @Test
        @DisplayName("도메인에 점이 없으면 기본 마스크를 반환한다")
        void givenDomainWithoutDot_whenMask_thenReturnsDefaultMask() {
            // when
            String result = EmailMaskingUtils.maskEmail("user@localhost");

            // then
            assertThat(result).isEqualTo(DEFAULT_MASK);
        }

        @Test
        @DisplayName("@가 여러 개인 이메일은 기본 마스크를 반환한다")
        void givenMultipleAtSymbols_whenMask_thenReturnsDefaultMask() {
            // when
            String result = EmailMaskingUtils.maskEmail("user@@gmail.com");

            // then
            assertThat(result).isEqualTo(DEFAULT_MASK);
        }

        @Test
        @DisplayName("로컬파트가 빈 이메일은 기본 마스크를 반환한다")
        void givenEmptyLocalPart_whenMask_thenReturnsDefaultMask() {
            // when
            String result = EmailMaskingUtils.maskEmail("@gmail.com");

            // then
            assertThat(result).isEqualTo(DEFAULT_MASK);
        }

        @Test
        @DisplayName("앞뒤 공백이 있는 이메일도 정상 처리한다")
        void givenEmailWithSpaces_whenMask_thenTrimsAndMasks() {
            // given
            String email = "  testuser@gmail.com  ";

            // when
            String result = EmailMaskingUtils.maskEmail(email);

            // then
            assertThat(result).isEqualTo("te***@gmail.com");
        }
    }

    @Nested
    @DisplayName("maskEmailForLog")
    class MaskEmailForLog {

        @Test
        @DisplayName("이메일의 로컬파트와 도메인 모두 마스킹한다")
        void givenValidEmail_whenMaskForLog_thenMasksBothParts() {
            // given
            String email = "testuser@gmail.com";

            // when
            String result = EmailMaskingUtils.maskEmailForLog(email);

            // then
            assertThat(result).isEqualTo("te***@g***.com");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "notanemail"})
        @DisplayName("잘못된 입력은 간단한 마스크를 반환한다")
        void givenInvalidInput_whenMaskForLog_thenReturnsSimpleMask(String email) {
            // when
            String result = EmailMaskingUtils.maskEmailForLog(email);

            // then
            assertThat(result).isEqualTo(SIMPLE_MASK);
        }

        @Test
        @DisplayName("도메인 부분도 첫 글자만 남기고 마스킹한다")
        void givenEmail_whenMaskForLog_thenMasksDomain() {
            // given
            String email = "user@naver.com";

            // when
            String result = EmailMaskingUtils.maskEmailForLog(email);

            // then
            assertThat(result).contains("n***");
            assertThat(result).endsWith(".com");
        }
    }

    @Nested
    @DisplayName("isValidEmailFormat")
    class IsValidEmailFormat {

        @Test
        @DisplayName("유효한 이메일 형식이면 true를 반환한다")
        void givenValidEmail_whenValidate_thenReturnsTrue() {
            // when & then
            assertThat(EmailMaskingUtils.isValidEmailFormat("user@gmail.com")).isTrue();
            assertThat(EmailMaskingUtils.isValidEmailFormat("test.user@naver.com")).isTrue();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("null 또는 빈 문자열이면 false를 반환한다")
        void givenNullOrEmpty_whenValidate_thenReturnsFalse(String email) {
            // when & then
            assertThat(EmailMaskingUtils.isValidEmailFormat(email)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {"notanemail", "user@", "@gmail.com", "user@localhost"})
        @DisplayName("잘못된 이메일 형식이면 false를 반환한다")
        void givenInvalidFormat_whenValidate_thenReturnsFalse(String email) {
            // when & then
            assertThat(EmailMaskingUtils.isValidEmailFormat(email)).isFalse();
        }
    }

    @Nested
    @DisplayName("인스턴스화 방지")
    class InstantiationPrevention {

        @Test
        @DisplayName("리플렉션으로 인스턴스 생성 시 AssertionError를 던진다")
        void whenInstantiateViaReflection_thenThrowsAssertionError() throws Exception {
            // given
            var constructor = EmailMaskingUtils.class.getDeclaredConstructor();
            constructor.setAccessible(true);

            // when & then
            assertThatThrownBy(constructor::newInstance)
                    .hasCauseInstanceOf(AssertionError.class);
        }
    }
}
