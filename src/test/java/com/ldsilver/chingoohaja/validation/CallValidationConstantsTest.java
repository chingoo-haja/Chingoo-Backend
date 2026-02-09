package com.ldsilver.chingoohaja.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CallValidationConstants 테스트")
class CallValidationConstantsTest {

    @Nested
    @DisplayName("CHANNEL_NAME_PATTERN")
    class ChannelNamePattern {

        @ParameterizedTest
        @ValueSource(strings = {"test_channel", "channel-123", "abc", "a"})
        @DisplayName("유효한 채널명은 패턴에 매칭된다")
        void givenValidChannelName_whenMatch_thenReturnsTrue(String channelName) {
            // when & then
            assertThat(CallValidationConstants.CHANNEL_NAME_PATTERN.matcher(channelName).matches())
                    .isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"한글채널", "channel\n", "test\ttab"})
        @DisplayName("비ASCII 또는 제어문자가 포함된 채널명은 매칭되지 않는다")
        void givenInvalidChannelName_whenMatch_thenReturnsFalse(String channelName) {
            // when & then
            assertThat(CallValidationConstants.CHANNEL_NAME_PATTERN.matcher(channelName).matches())
                    .isFalse();
        }

        @Test
        @DisplayName("빈 문자열은 매칭되지 않는다")
        void givenEmptyString_whenMatch_thenReturnsFalse() {
            // when & then
            assertThat(CallValidationConstants.CHANNEL_NAME_PATTERN.matcher("").matches())
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("CHANNEL_NAME_MAX_BYTES")
    class ChannelNameMaxBytes {

        @Test
        @DisplayName("64바이트 이하 채널명은 유효하다")
        void givenNameWithin64Bytes_whenCheckLength_thenValid() {
            // given
            String channelName = "a".repeat(64);

            // when
            int byteLength = channelName.getBytes(StandardCharsets.UTF_8).length;

            // then
            assertThat(byteLength).isLessThanOrEqualTo(CallValidationConstants.CHANNEL_NAME_MAX_BYTES);
        }

        @Test
        @DisplayName("64바이트 초과 채널명은 유효하지 않다")
        void givenNameExceeding64Bytes_whenCheckLength_thenInvalid() {
            // given
            String channelName = "a".repeat(65);

            // when
            int byteLength = channelName.getBytes(StandardCharsets.UTF_8).length;

            // then
            assertThat(byteLength).isGreaterThan(CallValidationConstants.CHANNEL_NAME_MAX_BYTES);
        }
    }

    @Nested
    @DisplayName("상수값 검증")
    class ConstantValues {

        @Test
        @DisplayName("기본 TTL은 1시간(3600초)이다")
        void defaultTtlIsOneHour() {
            assertThat(CallValidationConstants.DEFAULT_TTL_SECONDS_ONE_HOURS).isEqualTo(3600);
        }

        @Test
        @DisplayName("녹음 토큰 TTL은 24시간(86400초)이다")
        void recordingTokenTtlIs24Hours() {
            assertThat(CallValidationConstants.RECORDING_TOKEN_TTL_SECONDS).isEqualTo(86400);
        }

        @Test
        @DisplayName("Agora 최대 UID는 4294967295이다")
        void agoraMaxUidValue() {
            assertThat(CallValidationConstants.AGORA_MAX_UID).isEqualTo(4_294_967_295L);
        }

        @Test
        @DisplayName("기본 역할은 PUBLISHER이다")
        void defaultRoleIsPublisher() {
            assertThat(CallValidationConstants.DEFAULT_ROLE).isEqualTo("PUBLISHER");
        }
    }
}
