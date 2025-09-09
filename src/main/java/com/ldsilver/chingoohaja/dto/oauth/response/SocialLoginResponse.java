package com.ldsilver.chingoohaja.dto.oauth.response;

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

    public SocialLoginResponse withoutRefreshToken() {
        return new SocialLoginResponse(
                this.accessToken,
                null, // refresh_token을 null로 설정
                this.tokenType,
                this.expiresIn,
                this.issuedAt,
                this.userInfo
        );
    }

    public record UserInfo(
            @JsonProperty("id") Long id,
            @JsonProperty("email") String email,
            @JsonProperty("nickname") String nickname,
            @JsonProperty("real_name") String realName,
            @JsonProperty("profile_image_url") String profileImageUrl,
            @JsonProperty("user_type") String userType,
            @JsonProperty("provider") String provider,
            @JsonProperty("is_new_user") Boolean isNewUser,
            @JsonProperty("is_profile_complete") Boolean isProfileComplete,
            @JsonProperty("display_name") String displayName,
            @JsonProperty("age") Integer age,
            @JsonProperty("gender") String gender
    ) {

        public static UserInfo from(com.ldsilver.chingoohaja.domain.user.User user, boolean isNewUser) {
            return new UserInfo(
                    user.getId(),
                    user.getEmail(),
                    user.getNickname(),
                    user.getRealName(),
                    user.getProfileImageUrl(),
                    user.getUserType().name(),
                    user.getProvider(),
                    isNewUser,
                    user.isProfileComplete(),
                    user.getDisplayName(),
                    user.getAge(),
                    user.getGender() != null ? user.getGender().name() : null

            );
        }

        public boolean needsProfileCompletion() {
            return Boolean.TRUE.equals(isNewUser) && Boolean.FALSE.equals(isProfileComplete);
        }

        public boolean isOAuthUser() {
            return provider != null && !provider.equals("local");
        }

        public String getMaskedEmail() {
            if (email == null || !email.contains("@")) {
                return "***@***.***";
            }

            String[] parts = email.split("@");
            String localPart = parts[0];
            String domain = parts[1];

            if (localPart.length() <= 2) {
                return "***@" + domain;
            }

            return localPart.substring(0, 2) + "***@" + domain;
        }
    }
}
