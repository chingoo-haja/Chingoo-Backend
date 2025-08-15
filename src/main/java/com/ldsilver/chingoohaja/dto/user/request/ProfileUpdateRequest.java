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

    public ProfileUpdateRequest(String realName, String nickname, Gender gender, LocalDate birth) {
        this.realName = realName;
        this.nickname = nickname;
        this.gender = gender;
        this.birth = birth;
    }

    public static ProfileUpdateRequest of(String realName, String nickname, Gender gender, LocalDate birth) {
        return new ProfileUpdateRequest(realName, nickname, gender, birth);
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

    public boolean hasAnyChange() {
        return hasNicknameChange() || hasRealNameChange() || hasGenderChange() || hasBirthChange();
    }

    @JsonIgnore
    public String getTrimmedRealName() {
        return org.springframework.util.StringUtils.hasText(realName) ? realName.trim() : null;    }

    @JsonIgnore
    public String getTrimmedNickname() {
        return org.springframework.util.StringUtils.hasText(nickname) ? nickname.trim() : null;
    }
}
