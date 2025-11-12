package com.ldsilver.chingoohaja.dto.auth.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.validation.UserValidationConstants;
import jakarta.validation.constraints.*;

public record SignUpRequest(
        @NotBlank(message = UserValidationConstants.Email.REQUIRED)
        @Email(message = UserValidationConstants.Email.INVALID_FORMAT)
        @Size(max = UserValidationConstants.Email.MAX_LENGTH,
                message = UserValidationConstants.Email.TOO_LONG)
        @JsonProperty("email")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 20, message = "비밀번호는 8-20자여야 합니다.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?])[A-Za-z\\d!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]+$",
                message = "비밀번호는 영문, 숫자, 특수문자를 포함해야 합니다.")
        @JsonProperty("password")
        String password,

        @NotBlank(message = UserValidationConstants.RealName.REQUIRED)
        @Size(min = UserValidationConstants.RealName.MIN_LENGTH,
                max = UserValidationConstants.RealName.MAX_LENGTH,
                message = UserValidationConstants.RealName.INVALID_LENGTH)
        @Pattern(regexp = UserValidationConstants.RealName.PATTERN,
                message = UserValidationConstants.RealName.INVALID_FORMAT)
        @JsonProperty("real_name")
        String realName
) {
    public SignUpRequest {
        if (email != null) {
            email = email.trim();
        }
        if (realName != null) {
            realName = realName.trim();
        }
    }
}