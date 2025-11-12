package com.ldsilver.chingoohaja.dto.auth.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.validation.UserValidationConstants;
import com.ldsilver.chingoohaja.validation.validator.DeviceInfo;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = UserValidationConstants.Email.REQUIRED)
        @Email(message = UserValidationConstants.Email.INVALID_FORMAT)
        @JsonProperty("email")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @JsonProperty("password")
        String password,

        @DeviceInfo(nullable = true, message = "디바이스 정보 형식이 올바르지 않습니다.")
        @JsonProperty("device_info")
        String deviceInfo
) {
    // Compact Constructor - trim 처리
    public LoginRequest {
        if (email != null) {
            email = email.trim();
        }
    }

    // deviceInfo가 없는 생성자
    public LoginRequest(String email, String password) {
        this(email, password, null);
    }

    public String getSafeDeviceInfo() {
        return deviceInfo != null && !deviceInfo.trim().isEmpty()
                ? deviceInfo : "Unknown Device";
    }

    public boolean hasDeviceInfo() {
        return deviceInfo != null && !deviceInfo.trim().isEmpty();
    }
}