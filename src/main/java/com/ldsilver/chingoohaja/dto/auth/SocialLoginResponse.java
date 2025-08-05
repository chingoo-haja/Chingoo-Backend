package com.ldsilver.chingoohaja.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record SocialLoginResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") Long expiresIn,
        @JsonProperty("issued_at") LocalDateTime issuedAt,
        @JsonProperty("user_info") UserInfo userInfo
) {

    public SocialLoginResponse {
        tokenType = "Bearer";
        issuedAt = issuedAt != null ? issuedAt : LocalDateTime.now();
    }

    public static SocialLoginResponse of(
            String accessToken,
            String refreshToken,
            Long expiresIn,
            UserInfo userInfo) {
        return new SocialLoginResponse(
                accessToken,
                refreshToken,
                "Bearer",
                expiresIn,
                LocalDateTime.now(),
                userInfo
        );
    }

    public record UserInfo(
            @JsonProperty("id") Long id,
            @JsonProperty("email") String email,
            @JsonProperty("nickname") String nickname,
            @JsonProperty("profile_image_url") String profileImageUrl,
            @JsonProperty("user_type") String userType,
            @JsonProperty("is_new_user") Boolean isNewUser
    ) {

        public static UserInfo from(com.ldsilver.chingoohaja.domain.user.User user, boolean isNewUser) {
            return new UserInfo(
                    user.getId(),
                    user.getEmail(),
                    user.getNickname(),
                    user.getProfileImageUrl(),
                    user.getUserType().name(),
                    isNewUser
            );
        }
    }
}
