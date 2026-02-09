package com.ldsilver.chingoohaja.validation.validator;

import com.ldsilver.chingoohaja.domain.common.enums.TokenType;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("JwtTokenValidator 테스트")
class JwtTokenValidatorTest {

    private JwtToken.JwtTokenValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new JwtToken.JwtTokenValidator();
        context = mock(ConstraintValidatorContext.class);
    }

    private void initializeValidator(TokenType tokenType, boolean nullable) {
        JwtToken annotation = mock(JwtToken.class);
        when(annotation.tokenType()).thenReturn(tokenType);
        when(annotation.nullable()).thenReturn(nullable);
        validator.initialize(annotation);
    }

    private String createFakeJwt(int totalLength) {
        // 3개의 Base64 파트로 구성된 가짜 JWT 생성
        int partLength = (totalLength - 2) / 3; // 2개의 점(.) 제외
        String part = "a".repeat(Math.max(1, partLength));
        String jwt = part + "." + part + "." + part;
        // 총 길이 맞추기
        if (jwt.length() < totalLength) {
            String lastPart = part + "a".repeat(totalLength - jwt.length());
            jwt = part + "." + part + "." + lastPart;
        }
        return jwt;
    }

    @Nested
    @DisplayName("TokenType.ANY")
    class AnyTokenType {

        @BeforeEach
        void setUp() {
            initializeValidator(TokenType.ANY, false);
        }

        @Test
        @DisplayName("3파트 Base64 형식의 토큰은 유효하다")
        void givenValidJwtFormat_whenValidate_thenReturnsTrue() {
            // given
            String token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U";

            // when & then
            assertThat(validator.isValid(token, context)).isTrue();
        }

        @Test
        @DisplayName("null 입력은 유효하지 않다 (nullable=false)")
        void givenNull_whenValidate_thenReturnsFalse() {
            assertThat(validator.isValid(null, context)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "  ", "not.a.jwt.token", "onlyonepart", "two.parts"})
        @DisplayName("잘못된 JWT 형식은 유효하지 않다")
        void givenInvalidFormat_whenValidate_thenReturnsFalse(String token) {
            assertThat(validator.isValid(token, context)).isFalse();
        }

        @Test
        @DisplayName("Base64가 아닌 문자가 포함되면 유효하지 않다")
        void givenNonBase64Chars_whenValidate_thenReturnsFalse() {
            // given
            String token = "abc!@#.def$%^.ghi&*(";

            // when & then
            assertThat(validator.isValid(token, context)).isFalse();
        }
    }

    @Nested
    @DisplayName("TokenType.ACCESS")
    class AccessTokenType {

        @BeforeEach
        void setUp() {
            initializeValidator(TokenType.ACCESS, false);
        }

        @Test
        @DisplayName("100~1024자 범위의 토큰은 유효하다")
        void givenValidLengthToken_whenValidate_thenReturnsTrue() {
            // given
            String token = createFakeJwt(200);

            // when & then
            assertThat(validator.isValid(token, context)).isTrue();
        }

        @Test
        @DisplayName("100자 미만 토큰은 유효하지 않다")
        void givenTooShortToken_whenValidate_thenReturnsFalse() {
            // given
            String token = createFakeJwt(50);

            // when & then
            assertThat(validator.isValid(token, context)).isFalse();
        }

        @Test
        @DisplayName("1024자 초과 토큰은 유효하지 않다")
        void givenTooLongToken_whenValidate_thenReturnsFalse() {
            // given
            String token = createFakeJwt(1025);

            // when & then
            assertThat(validator.isValid(token, context)).isFalse();
        }
    }

    @Nested
    @DisplayName("TokenType.REFRESH")
    class RefreshTokenType {

        @BeforeEach
        void setUp() {
            initializeValidator(TokenType.REFRESH, false);
        }

        @Test
        @DisplayName("100~2048자 범위의 토큰은 유효하다")
        void givenValidLengthToken_whenValidate_thenReturnsTrue() {
            // given
            String token = createFakeJwt(500);

            // when & then
            assertThat(validator.isValid(token, context)).isTrue();
        }

        @Test
        @DisplayName("2048자 초과 토큰은 유효하지 않다")
        void givenTooLongToken_whenValidate_thenReturnsFalse() {
            // given
            String token = createFakeJwt(2049);

            // when & then
            assertThat(validator.isValid(token, context)).isFalse();
        }
    }

    @Nested
    @DisplayName("nullable = true")
    class NullableTrue {

        @BeforeEach
        void setUp() {
            initializeValidator(TokenType.ANY, true);
        }

        @Test
        @DisplayName("null 입력은 유효하다")
        void givenNull_whenValidate_thenReturnsTrue() {
            assertThat(validator.isValid(null, context)).isTrue();
        }
    }
}
