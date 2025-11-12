package com.ldsilver.chingoohaja.dto.auth.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.domain.user.enums.Gender;
import com.ldsilver.chingoohaja.validation.CommonValidationConstants;
import com.ldsilver.chingoohaja.validation.UserValidationConstants;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record SignUpRequest(
        @NotBlank(message = UserValidationConstants.Email.REQUIRED)
        @Email(message = UserValidationConstants.Email.INVALID_FORMAT)
        @Size(max = UserValidationConstants.Email.MAX_LENGTH,
                message = UserValidationConstants.Email.TOO_LONG)
        @JsonProperty("email")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 20, message = "비밀번호는 8-20자여야 합니다.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]+$",
                message = "비밀번호는 영문, 숫자, 특수문자를 포함해야 합니다.")
        @JsonProperty("password")
        String password,

        @NotBlank(message = UserValidationConstants.Nickname.REQUIRED)
        @Size(min = UserValidationConstants.Nickname.MIN_LENGTH,
                max = UserValidationConstants.Nickname.MAX_LENGTH,
                message = UserValidationConstants.Nickname.INVALID_LENGTH)
        @Pattern(regexp = UserValidationConstants.Nickname.PATTERN,
                message = UserValidationConstants.Nickname.INVALID_FORMAT)
        @JsonProperty("nickname")
        String nickname,

        @NotBlank(message = UserValidationConstants.RealName.REQUIRED)
        @Size(min = UserValidationConstants.RealName.MIN_LENGTH,
                max = UserValidationConstants.RealName.MAX_LENGTH,
                message = UserValidationConstants.RealName.INVALID_LENGTH)
        @Pattern(regexp = UserValidationConstants.RealName.PATTERN,
                message = UserValidationConstants.RealName.INVALID_FORMAT)
        @JsonProperty("real_name")
        String realName,

        @NotNull(message = "성별은 필수입니다.")
        @JsonProperty("gender")
        Gender gender,

        @NotNull(message = "생년월일은 필수입니다.")
        @Past(message = UserValidationConstants.Birth.LEAST_DOB)
        @JsonFormat(shape = JsonFormat.Shape.STRING,
                pattern = CommonValidationConstants.Date.DATE_PATTERN)
        @JsonProperty("birth")
        LocalDate birth
) {
    // Compact Constructor - validation 로직
    public SignUpRequest {
        if (email != null) {
            email = email.trim();
        }
        if (nickname != null) {
            nickname = nickname.trim();
        }
        if (realName != null) {
            realName = realName.trim();
        }
    }
}