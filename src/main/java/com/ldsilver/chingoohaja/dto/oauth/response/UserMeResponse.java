package com.ldsilver.chingoohaja.dto.oauth.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.common.util.EmailMaskingUtils;
import com.ldsilver.chingoohaja.domain.user.User;

import java.time.LocalDateTime;

public record UserMeResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("email") String email,
        @JsonProperty("nickname") String nickname,
        @JsonProperty("real_name") String realName,
        @JsonProperty("profile_image_url") String profileImageUrl,
        @JsonProperty("user_type") String userType,
        @JsonProperty("provider") String provider,
        @JsonProperty("is_profile_complete") boolean isProfileComplete,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("age") Integer age,
        @JsonProperty("gender") String gender,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("updated_at") LocalDateTime updatedAt
) {
    public static UserMeResponse from(User user) {
        return new UserMeResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRealName(),
                user.getProfileImageUrl(),
                user.getUserType().name(),
                user.getProvider(),
                user.isProfileComplete(),
                user.getDisplayName(),
                user.getAge(),
                user.getGender() != null ? user.getGender().name() : null,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    public boolean isOAuthUser() {
        return provider != null && !provider.equals("local");
    }


    public boolean needsProfileCompletion() {
        return !isProfileComplete;
    }


    public String getMaskedEmail() {
        return EmailMaskingUtils.maskEmail(email);
    }

    public boolean hasValidProfileImage() {
        return profileImageUrl != null &&
                !profileImageUrl.trim().isEmpty() &&
                !profileImageUrl.contains("default");
    }
}
