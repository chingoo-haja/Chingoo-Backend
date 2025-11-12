package com.ldsilver.chingoohaja.dto.auth.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.validation.UserValidationConstants;
import com.ldsilver.chingoohaja.validation.validator.DeviceInfo;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginRequest {
    @NotBlank(message = UserValidationConstants.Email.REQUIRED)
    @Email(message = UserValidationConstants.Email.INVALID_FORMAT)
    @JsonProperty("email")
    private String email;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @JsonProperty("password")
    private String password;

    @DeviceInfo(nullable = true, message = "디바이스 정보 형식이 올바르지 않습니다.")
    @JsonProperty("device_info")
    private String deviceInfo;

    @Setter
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String clientIp;

    public LoginRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public LoginRequest(String email, String password, String deviceInfo) {
        this.email = email;
        this.password = password;
        this.deviceInfo = deviceInfo;
    }

    public String getTrimmedEmail() {
        return email != null ? email.trim() : null;
    }

    public String getSafeDeviceInfo() {
        return deviceInfo != null && !deviceInfo.trim().isEmpty()
                ? deviceInfo : "Unknown Device";
    }

    public boolean hasDeviceInfo() {
        return deviceInfo != null && !deviceInfo.trim().isEmpty();
    }
}
