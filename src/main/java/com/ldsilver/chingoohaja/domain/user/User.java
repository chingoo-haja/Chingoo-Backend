package com.ldsilver.chingoohaja.domain.user;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.common.BaseEntity;
import com.ldsilver.chingoohaja.domain.user.enums.Gender;
import com.ldsilver.chingoohaja.domain.user.enums.UserType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

import static com.ldsilver.chingoohaja.validation.CommonValidationConstants.MAX_PROFILE_IMAGE_URL_LENGTH;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    @Email()
    @NotBlank
    private String email;

    @Column(nullable = true, length = 100)
    private String password;

    @Column(nullable = false,unique = true)
    @NotBlank
    @Size(min = 1, max = 100)
    private String nickname;

    @Column(nullable = false)
    private String realName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Column(nullable = false)
    private LocalDate birth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserType userType = UserType.USER;

    @Size(max = MAX_PROFILE_IMAGE_URL_LENGTH)
    @Column(nullable = true, length = MAX_PROFILE_IMAGE_URL_LENGTH)
    private String profileImageUrl;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String providerId;

    public static User of(
            String email,
            String nickname,
            String realName,
            Gender gender,
            LocalDate birth,
            UserType userType,
            String profileImageUrl,
            String provider,
            String providerId
    ) {
        User user = new User();
        user.email = email;
        user.nickname = nickname;
        user.realName = realName;
        user.gender = gender;
        user.birth = birth;
        user.userType = userType;
        user.profileImageUrl = profileImageUrl;
        user.provider = provider;
        user.providerId = providerId;
        return user;
    }

    public static User ofLocal(
            String email,
            String password,
            String nickname,
            String realName,
            Gender gender,
            LocalDate birth,
            String profileImageUrl
    ) {
        User user = new User();
        user.email = email;
        user.password = password;
        user.nickname = nickname;
        user.realName = realName;
        user.gender = gender;
        user.birth = birth;
        user.userType = UserType.USER;
        user.profileImageUrl = profileImageUrl;
        user.provider = "local";
        user.providerId = email;
        return user;
    }

    public boolean isOAuthUser() {
        return provider != null && !provider.equals("local");
    }

    public boolean isLocalUser() {
        return "local".equals(provider);
    }

    public boolean isProviderUser(String providerName) {
        return provider != null && provider.equalsIgnoreCase(providerName);
    }

    public void updatePassword(String newPassword) {
        if (newPassword != null && !newPassword.trim().isEmpty()) {
            this.password = newPassword;
        }
    }

    public void updateProfileImage(String newProfileImageUrl) {
        if (newProfileImageUrl != null) {
            String trimmed = newProfileImageUrl.trim();
            if (!trimmed.isEmpty()) {
                if (trimmed.length() > MAX_PROFILE_IMAGE_URL_LENGTH) {
                    throw new CustomException(ErrorCode.INVALID_IMAGE_URL_LENGTH);
                }
                this.profileImageUrl = trimmed;
            }
        }
    }

    public void updateNickname(String newNickname) {
        if (newNickname != null) {
            String trimmed = newNickname.trim();
            if (!trimmed.isEmpty()) {
                this.nickname = trimmed;
            }
        }
    }

    public void updateRealName(String newRealName) {
        if (newRealName != null) {
            String trimmed = newRealName.trim();
            if (!trimmed.isEmpty()) {
                this.realName = trimmed;
            }
        }
    }

    public void updateGender(Gender newGender) {
        if (newGender != null) {
            this.gender = newGender;
        }
    }

    public void updateBirth(LocalDate newBirth) {
        if (newBirth != null) {
            this.birth = newBirth;
        }
    }

    /**
     * 사용자 타입 업데이트 (일반 사용자 → 보호자 등)
     */
    public void updateUserType(UserType newUserType) {
        if (newUserType != null) {
            this.userType = newUserType;
        }
    }

    /**
     * OAuth Provider 정보 업데이트 (Provider 연동 변경 시)
     */
    public void updateProviderInfo(String newProvider, String newProviderId) {
        if (newProvider != null && !newProvider.trim().isEmpty() &&
                newProviderId != null && !newProviderId.trim().isEmpty()) {
            this.provider = newProvider.trim();
            this.providerId = newProviderId.trim();
        }
    }

    public void updateProfile(String newRealName, String newNickname, Gender newGender,
                              LocalDate newBirth, String newProfileImageUrl) {
        updateRealName(newRealName);
        updateNickname(newNickname);
        updateGender(newGender);
        updateBirth(newBirth);
        updateProfileImage(newProfileImageUrl);
    }

    public int getAge() {
        if (birth == null) {
            return 0;
        }
        return LocalDate.now().getYear() - birth.getYear();
    }

    /**
     * 프로필 완성도 확인 (OAuth 로그인 후 추가 정보 입력 필요 여부)
     */
    public boolean isProfileComplete() {
        return realName != null && !realName.trim().isEmpty() &&
                nickname != null && !nickname.trim().isEmpty() &&
                gender != null &&
                birth != null &&
                profileImageUrl != null && !profileImageUrl.trim().isEmpty();
    }

    public String getDisplayName() {
        if (realName != null && !realName.trim().isEmpty()) {
            return realName;
        }
        return nickname;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        User user = (User) obj;
        return id != null && id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

}
