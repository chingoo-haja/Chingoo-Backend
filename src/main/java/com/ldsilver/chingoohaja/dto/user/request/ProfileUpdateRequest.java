package com.ldsilver.chingoohaja.dto.user.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.domain.user.enums.Gender;
import com.ldsilver.chingoohaja.validation.CommonValidationConstants;
import com.ldsilver.chingoohaja.validation.UserValidationConstants;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileUpdateRequest {

    @Size(
            min = UserValidationConstants.RealName.MIN_LENGTH,
            max = UserValidationConstants.RealName.MAX_LENGTH,
            message = UserValidationConstants.RealName.INVALID_LENGTH
    )
    @Pattern(
            regexp = UserValidationConstants.RealName.PATTERN,
            message = UserValidationConstants.RealName.INVALID_FORMAT
    )
    @JsonProperty("real_name")
    private String realName;

    @Size(
            min = UserValidationConstants.Nickname.MIN_LENGTH,
            max = UserValidationConstants.Nickname.MAX_LENGTH,
            message = UserValidationConstants.Nickname.INVALID_LENGTH
    )
    @Pattern(
            regexp = UserValidationConstants.Nickname.PATTERN,
            message = UserValidationConstants.Nickname.INVALID_FORMAT
    )
    @JsonProperty("nickname")
    private String nickname;

    @JsonProperty("gender")
    private Gender gender;

    @Past(message = UserValidationConstants.Birth.LEAST_DOB)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = CommonValidationConstants.Date.DATE_PATTERN)
    @JsonProperty("birth")
    private LocalDate birth;

    @Size(
            min = UserValidationConstants.PhoneNumber.MIN_LENGTH,
            max = UserValidationConstants.PhoneNumber.MAX_LENGTH,
            message = UserValidationConstants.PhoneNumber.INVALID_LENGTH
    )
    @Pattern(
            regexp = UserValidationConstants.PhoneNumber.PATTERN,
            message = UserValidationConstants.PhoneNumber.INVALID_FORMAT
    )
    @JsonProperty("phone_number")
    private String phoneNumber;

    public ProfileUpdateRequest(String realName, String nickname, Gender gender, LocalDate birth, String phoneNumber) {
        this.realName = realName;
        this.nickname = nickname;
        this.gender = gender;
        this.birth = birth;
        this.phoneNumber = phoneNumber;
    }

    public static ProfileUpdateRequest of(String realName, String nickname, Gender gender, LocalDate birth, String phoneNumber) {
        return new ProfileUpdateRequest(realName, nickname, gender, birth, phoneNumber);
    }

    public boolean hasNicknameChange() {
        return nickname != null;
    }

    public boolean hasRealNameChange() {
        return realName != null;
    }

    public boolean hasGenderChange() {
        return gender != null;
    }

    public boolean hasBirthChange() {
        return birth != null;
    }

    public boolean hasPhoneNumberChange() { return phoneNumber != null; }

    public boolean hasAnyChange() {
        return hasNicknameChange() || hasRealNameChange() || hasGenderChange()
                || hasBirthChange() || hasPhoneNumberChange();
    }

    @JsonIgnore
    public String getTrimmedRealName() {
        return StringUtils.hasText(realName) ? realName.trim() : null;    }

    @JsonIgnore
    public String getTrimmedNickname() {
        return StringUtils.hasText(nickname) ? nickname.trim() : null;
    }

    @JsonIgnore
    public String getTrimmedPhoneNumber() {
        return StringUtils.hasText(phoneNumber) ? phoneNumber.trim() : null;
    }
}
