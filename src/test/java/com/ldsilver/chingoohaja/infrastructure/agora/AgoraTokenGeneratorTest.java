package com.ldsilver.chingoohaja.infrastructure.agora;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.config.AgoraProperties;
import io.agora.media.RtcTokenBuilder2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("AgoraTokenGenerator 테스트")
class AgoraTokenGeneratorTest {

    private AgoraTokenGenerator agoraTokenGenerator;
    private AgoraProperties agoraProperties;

    @BeforeEach
    void setUp() {
        agoraProperties = mock(AgoraProperties.class);
        when(agoraProperties.getAppId()).thenReturn("970CA35de60c44645bbae8a215061b33");
        when(agoraProperties.getAppCertificate()).thenReturn("5CFd2fd1755d40ecb72977518be15d3b");
        when(agoraProperties.getTokenExpirationInSeconds()).thenReturn(3600);

        agoraTokenGenerator = new AgoraTokenGenerator(agoraProperties);
    }

    @Nested
    @DisplayName("generateRtcToken - 전체 파라미터")
    class GenerateRtcTokenFull {

        @Test
        @DisplayName("유효한 파라미터로 RTC 토큰을 생성한다")
        void givenValidParams_whenGenerate_thenReturnsToken() {
            // when
            String token = agoraTokenGenerator.generateRtcToken(
                    "test_channel", 1, RtcTokenBuilder2.Role.ROLE_PUBLISHER, 3600);

            // then
            assertThat(token).isNotNull().isNotBlank();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("채널명이 null이거나 빈 문자열이면 예외를 던진다")
        void givenNullOrEmptyChannelName_whenGenerate_thenThrowsException(String channelName) {
            // when & then
            assertThatThrownBy(() -> agoraTokenGenerator.generateRtcToken(
                    channelName, 1, RtcTokenBuilder2.Role.ROLE_PUBLISHER, 3600))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CHANNEL_NAME_REQUIRED));
        }

        @Test
        @DisplayName("채널명에 제어 문자가 포함되면 예외를 던진다")
        void givenControlCharInChannelName_whenGenerate_thenThrowsException() {
            // when & then - 제어 문자 (ASCII 0x01)
            assertThatThrownBy(() -> agoraTokenGenerator.generateRtcToken(
                    "channel\u0001name", 1, RtcTokenBuilder2.Role.ROLE_PUBLISHER, 3600))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CHANNEL_NAME_INVALID));
        }

        @Test
        @DisplayName("UID가 음수이면 예외를 던진다")
        void givenNegativeUid_whenGenerate_thenThrowsException() {
            // when & then
            assertThatThrownBy(() -> agoraTokenGenerator.generateRtcToken(
                    "test_channel", -1, RtcTokenBuilder2.Role.ROLE_PUBLISHER, 3600))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.UID_NOT_MINUS));
        }

        @Test
        @DisplayName("Role이 null이면 예외를 던진다")
        void givenNullRole_whenGenerate_thenThrowsException() {
            // when & then
            assertThatThrownBy(() -> agoraTokenGenerator.generateRtcToken(
                    "test_channel", 1, null, 3600))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.ROLE_REQUIRED));
        }

        @Test
        @DisplayName("만료 시간이 0 이하이면 예외를 던진다")
        void givenInvalidExpiration_whenGenerate_thenThrowsException() {
            // when & then
            assertThatThrownBy(() -> agoraTokenGenerator.generateRtcToken(
                    "test_channel", 1, RtcTokenBuilder2.Role.ROLE_PUBLISHER, 0))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_EXPIRED_TIME));
        }
    }

    @Nested
    @DisplayName("generateRtcToken - 기본 파라미터")
    class GenerateRtcTokenDefault {

        @Test
        @DisplayName("채널명과 UID만으로 기본 설정 토큰을 생성한다")
        void givenChannelAndUid_whenGenerate_thenReturnsTokenWithDefaults() {
            // when
            String token = agoraTokenGenerator.generateRtcToken("test_channel", 1);

            // then
            assertThat(token).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("UID가 0이어도 정상적으로 토큰을 생성한다")
        void givenZeroUid_whenGenerate_thenReturnsToken() {
            // when & then
            assertThatCode(() -> agoraTokenGenerator.generateRtcToken("test_channel", 0))
                    .doesNotThrowAnyException();
        }
    }
}
