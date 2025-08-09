package com.ldsilver.chingoohaja.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.domain.user.enums.Gender;
import com.ldsilver.chingoohaja.dto.auth.response.GoogleApiResponse;
import com.ldsilver.chingoohaja.dto.auth.response.KakaoApiResponse;

public record OAuthUserInfo(
        @JsonProperty("provider_id") String providerId,
        @JsonProperty("provider") String provider,
        @JsonProperty("email") String email,
        @JsonProperty("name") String name,
        @JsonProperty("nickname") String nickname,
        @JsonProperty("profile_image_url") String profileImageUrl,
        @JsonProperty("gender") Gender gender
) {

    public static OAuthUserInfo fromKakao(KakaoApiResponse kakao) {
        return new OAuthUserInfo(
                kakao.id(),
            "kakao",
            kakao.kakaoAccount() != null ? kakao.kakaoAccount().email() : null,
            extractKakaoName(kakao),
            extractKakaoNickname(kakao),
            extractKakaoProfileImage(kakao),
            extractKakaoGender(kakao)
        );
    }

    public static OAuthUserInfo fromGoogle(GoogleApiResponse google) {
        return new OAuthUserInfo(
                google.id(),
                "google",
                google.email(),
                google.name(),
                google.givenName() != null ? google.givenName() : google.name(),
                google.picture(),
                null
        );
    }


    private static String extractKakaoName(KakaoApiResponse kakao) {
        if (kakao.kakaoAccount() != null && kakao.kakaoAccount().profile() != null) {
            return kakao.kakaoAccount().profile().nickname();
        }
        return null;
    }

    private static String extractKakaoNickname(KakaoApiResponse kakao) {
        if (kakao.properties() != null && kakao.properties().nickname() != null) {
            return kakao.properties().nickname();
        }
        return extractKakaoName(kakao);
    }

    private static String extractKakaoProfileImage(KakaoApiResponse kakao) {
        if (kakao.properties() != null && kakao.properties().profileImage() != null) {
            return kakao.properties().profileImage();
        }
        if (kakao.kakaoAccount() != null && kakao.kakaoAccount().profile() != null) {
            return kakao.kakaoAccount().profile().profileImageUrl();
        }
        return null;
    }

    private static Gender extractKakaoGender(KakaoApiResponse kakao) {
        if (kakao.kakaoAccount() != null && kakao.kakaoAccount().gender() != null) {
            return "female".equalsIgnoreCase(kakao.kakaoAccount().gender()) ?
                    Gender.FEMALE : Gender.MALE;
        }
        return null;
    }
}
