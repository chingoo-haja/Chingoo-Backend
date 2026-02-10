package com.ldsilver.chingoohaja.infrastructure.oauth;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.dto.oauth.OAuthUserInfo;
import com.ldsilver.chingoohaja.dto.oauth.response.TokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OAuthClientFactory 테스트")
class OAuthClientFactoryTest {

    private OAuthClientFactory oAuthClientFactory;

    private final OAuthClient kakaoClient = new StubOAuthClient("kakao");
    private final OAuthClient googleClient = new StubOAuthClient("google");

    @BeforeEach
    void setUp() {
        oAuthClientFactory = new OAuthClientFactory(List.of(kakaoClient, googleClient));
    }

    @Nested
    @DisplayName("getClient")
    class GetClient {

        @Test
        @DisplayName("등록된 프로바이더명으로 클라이언트를 반환한다")
        void givenRegisteredProvider_whenGetClient_thenReturnsClient() {
            // when
            OAuthClient client = oAuthClientFactory.getClient("kakao");

            // then
            assertThat(client).isEqualTo(kakaoClient);
            assertThat(client.getProviderName()).isEqualTo("kakao");
        }

        @Test
        @DisplayName("대소문자를 무시하고 클라이언트를 반환한다")
        void givenUpperCaseProvider_whenGetClient_thenReturnsClient() {
            // when
            OAuthClient client = oAuthClientFactory.getClient("KAKAO");

            // then
            assertThat(client).isEqualTo(kakaoClient);
        }

        @Test
        @DisplayName("공백이 포함된 프로바이더명도 정규화하여 반환한다")
        void givenProviderWithSpaces_whenGetClient_thenReturnsClient() {
            // when
            OAuthClient client = oAuthClientFactory.getClient("  google  ");

            // then
            assertThat(client).isEqualTo(googleClient);
        }

        @Test
        @DisplayName("지원하지 않는 프로바이더이면 예외를 던진다")
        void givenUnsupportedProvider_whenGetClient_thenThrowsException() {
            // when & then
            assertThatThrownBy(() -> oAuthClientFactory.getClient("naver"))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.OAUTH_PROVIDER_NOT_SUPPORTED));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "\t"})
        @DisplayName("null 또는 빈 문자열이면 예외를 던진다")
        void givenNullOrBlankProvider_whenGetClient_thenThrowsException(String provider) {
            // when & then
            assertThatThrownBy(() -> oAuthClientFactory.getClient(provider))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.OAUTH_PROVIDER_NOT_SUPPORTED));
        }
    }

    @Nested
    @DisplayName("getSupportedProviders")
    class GetSupportedProviders {

        @Test
        @DisplayName("등록된 모든 프로바이더 목록을 반환한다")
        void whenGetSupportedProviders_thenReturnsAllProviders() {
            // when
            List<String> providers = oAuthClientFactory.getSupportedProviders();

            // then
            assertThat(providers).hasSize(2);
            assertThat(providers).containsExactlyInAnyOrder("kakao", "google");
        }
    }

    @Nested
    @DisplayName("isProviderSupported")
    class IsProviderSupported {

        @Test
        @DisplayName("등록된 프로바이더이면 true를 반환한다")
        void givenRegisteredProvider_whenCheck_thenReturnsTrue() {
            assertThat(oAuthClientFactory.isProviderSupported("kakao")).isTrue();
            assertThat(oAuthClientFactory.isProviderSupported("GOOGLE")).isTrue();
        }

        @Test
        @DisplayName("미등록 프로바이더이면 false를 반환한다")
        void givenUnregisteredProvider_whenCheck_thenReturnsFalse() {
            assertThat(oAuthClientFactory.isProviderSupported("naver")).isFalse();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("null 또는 빈 문자열이면 false를 반환한다")
        void givenNullOrBlank_whenCheck_thenReturnsFalse(String provider) {
            assertThat(oAuthClientFactory.isProviderSupported(provider)).isFalse();
        }
    }

    /**
     * 테스트용 OAuthClient 스텁 구현체
     */
    private static class StubOAuthClient implements OAuthClient {
        private final String providerName;

        StubOAuthClient(String providerName) {
            this.providerName = providerName;
        }

        @Override
        public TokenResponse exchangeCodeForToken(String code, String codeVerifier) {
            return null;
        }

        @Override
        public OAuthUserInfo getUserInfo(String accessToken) {
            return null;
        }

        @Override
        public String getProviderName() {
            return providerName;
        }
    }
}
