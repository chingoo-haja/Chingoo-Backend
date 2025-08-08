package com.ldsilver.chingoohaja.dto.auth.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoApiResponse(
        @JsonProperty("id") String id,
        @JsonProperty("kakao_account") KakaoAccount kakaoAccount,
        @JsonProperty("properties") Properties properties
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoAccount(
            @JsonProperty("email") String email,
            @JsonProperty("gender") String gender,
            @JsonProperty("profile") Profile profile
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Profile(
            @JsonProperty("nickname") String nickname,
            @JsonProperty("profile_image_url") String profileImageUrl
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Properties(
            @JsonProperty("nickname") String nickname,
            @JsonProperty("profile_image") String profileImage
    ) {}
}
