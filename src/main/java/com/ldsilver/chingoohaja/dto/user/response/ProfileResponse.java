package com.ldsilver.chingoohaja.dto.user.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.common.util.EmailMaskingUtils;
import com.ldsilver.chingoohaja.domain.user.User;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProfileResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("email") String email,
        @JsonProperty("nickname") String nickname,
        @JsonProperty("real_name") String realName,
        @JsonProperty("gender") String gender,
        @JsonProperty("birth") LocalDate birth,
        @JsonProperty("age") Integer age,
        @JsonProperty("phone_number") String phoneNumber,
        @JsonProperty("user_type") String userType,
        @JsonProperty("profile_image_url") String profileImageUrl,
        @JsonProperty("provider") String provider,
        @JsonProperty("is_profile_complete") boolean isProfileComplete,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("updated_at") LocalDateTime updatedAt
) {
    public static ProfileResponse from(User user) {
        return new ProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRealName(),
                user.getGender() != null ? user.getGender().name() : null,
                user.getBirth(),
                user.getAge(),
                user.getPhoneNumber(),
                user.getUserType().name(),
                user.getProfileImageUrl(),
                user.getProvider(),
                user.isProfileComplete(),
                user.getDisplayName(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    public String getMaskedEmail() {
        return EmailMaskingUtils.maskEmail(email);
    }

    public boolean isOAuthUser() {
        return provider != null && !provider.equals("local");
    }

    public boolean hasValidProfileImage() {
        return profileImageUrl != null &&
                !profileImageUrl.trim().isEmpty() &&
                !profileImageUrl.contains("default");
    }

    public boolean needsProfileCompletion() {
        return !isProfileComplete;
    }
}
