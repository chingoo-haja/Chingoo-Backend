package com.ldsilver.chingoohaja.dto.auth.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.domain.common.enums.Provider;
import com.ldsilver.chingoohaja.validation.AuthValidationConstants;
import com.ldsilver.chingoohaja.validation.validator.DeviceInfo;
import com.ldsilver.chingoohaja.validation.validator.OAuthAuthCode;
import com.ldsilver.chingoohaja.validation.validator.OAuthState;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialLoginRequest {

    @NotBlank(message = AuthValidationConstants.OAuth.AUTH_CODE_REQUIRED)
    @OAuthAuthCode(
            provider = Provider.ANY,
            message = AuthValidationConstants.OAuth.AUTH_CODE_INVALID_LENGTH
    )
    @JsonProperty("code")
    private String code;

    @NotBlank(message = AuthValidationConstants.OAuth.STATE_REQUIRED)
    @OAuthState(message = AuthValidationConstants.OAuth.STATE_TOO_LONG)
    @JsonProperty("state")
    private String state;

    @DeviceInfo(
            nullable = true,
            message = AuthValidationConstants.Device.DEVICE_INFO_TOO_LONG
    )
    @JsonProperty("device_info")
    private String deviceInfo;

    @Size(
            max = AuthValidationConstants.OAuth.MAX_REDIRECT_URI_LENGTH,
            message = AuthValidationConstants.OAuth.REDIRECT_URI_TOO_LONG
    )
    @JsonProperty("redirect_uri")
    private String redirectUri;

    // 서버에서 동적으로 설정되는 필드들 (검증 제외)
    @Setter
    @JsonProperty("client_ip")
    private String clientIp;

    @JsonProperty("code_verifier")
    private String codeVerifier;

    public SocialLoginRequest(String code, String state, String deviceInfo) {
        this.code = code;
        this.state = state;
        this.deviceInfo = deviceInfo;
    }

    public void setCodeVerifier(String codeVerifier) {
        this.codeVerifier = codeVerifier;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    // 비즈니스 로직 메서드들
    public boolean hasDeviceInfo() {
        return deviceInfo != null && !deviceInfo.trim().isEmpty();
    }

    public boolean hasCodeVerifier() {
        return codeVerifier != null && !codeVerifier.trim().isEmpty();
    }

    public String getSafeDeviceInfo() {
        return hasDeviceInfo() ? deviceInfo : "Unknown Device";
    }

    // Provider별 검증 메서드
    public boolean isValidForProvider(String provider) {
        return switch (provider.toLowerCase()) {
            case "kakao" -> isValidKakaoRequest();
            case "google" -> isValidGoogleRequest();
            default -> isValidGenericRequest();
        };
    }

    private boolean isValidKakaoRequest() {
        return code != null && code.length() >= 10 && code.length() <= 100;
    }

    private boolean isValidGoogleRequest() {
        return code != null && code.length() >= 20 && code.length() <= 200;
    }

    private boolean isValidGenericRequest() {
        return code != null && code.length() >= 10;
    }
}
