package com.ldsilver.chingoohaja.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.oauth")
public class OAuthProperties {

    @Valid
    private KakaoProperties kakao = new KakaoProperties();

    @Valid
    private GoogleProperties google = new GoogleProperties();

    @Getter
    @Setter
    public static class KakaoProperties {
        @NotBlank(message = "카카오 클라이언트 ID는 필수입니다.")
        private String clientId;

        @NotBlank(message = "카카오 클라이언트 시크릿은 필수입니다.")
        private String clientSecret;

        @NotBlank(message = "카카오 리다이렉트 URI는 필수입니다.")
        @Size(max = 512, message = "리다이렉트 URI가 너무 깁니다.")
        private String redirectUri;

        @Size(max = 512, message = "모바일 리다이렉트 URI가 너무 깁니다.")
        private String redirectUriMobile;

        private String scope = "profile_nickname, profile_image, account_email";

        private String authUrl = "https://kauth.kakao.com/oauth/authorize";
        private String tokenUrl = "https://kauth.kakao.com/oauth/token";
        private String userInfoUrl = "https://kapi.kakao.com/v2/user/me";

        public String getRedirectUri(boolean isMobile) {
            if (isMobile && redirectUriMobile != null && !redirectUriMobile.isEmpty()) {
                return redirectUriMobile;
            }
            return redirectUri;
        }

        public String getAuthorizationUrl(String state, String codeChallenge, boolean isMobile) {
            String redirectUriToUse = getRedirectUri(isMobile);
            return String.format("%s?client_id=%s&redirect_uri=%s&response_type=code&scope=%s&state=%s&code_challenge=%s&code_challenge_method=S256",
                    authUrl, clientId, redirectUriToUse, scope, state, codeChallenge);
        }
    }

    @Getter
    @Setter
    public static class GoogleProperties {
        @NotBlank(message = "구글 클라이언트 ID는 필수입니다.")
        private String clientId;

        @NotBlank(message = "구글 클라이언트 시크릿은 필수입니다.")
        private String clientSecret;

        @NotBlank(message = "구글 리다이렉트 URI는 필수입니다.")
        @Size(max = 512, message = "리다이렉트 URI가 너무 깁니다.")
        private String redirectUri;

        @Size(max = 512, message = "모바일 리다이렉트 URI가 너무 깁니다.")
        private String redirectUriMobile;

        private String scope = "openid email profile";

        private String authUrl = "https://accounts.google.com/o/oauth2/v2/auth";
        private String tokenUrl = "https://oauth2.googleapis.com/token";
        private String userInfoUrl = "https://www.googleapis.com/oauth2/v2/userinfo";

        public String getRedirectUri(boolean isMobile) {
            if (isMobile && redirectUriMobile != null && !redirectUriMobile.isEmpty()) {
                return redirectUriMobile;
            }
            return redirectUri;
        }

        public String getAuthorizationUrl(String state, String codeChallenge, boolean isMobile) {
            String redirectUriToUse = getRedirectUri(isMobile);
            return String.format("%s?client_id=%s&redirect_uri=%s&response_type=code&scope=%s&state=%s&code_challenge=%s&code_challenge_method=S256&access_type=offline",
                    authUrl, clientId, redirectUriToUse, scope, state, codeChallenge);
        }
    }
}