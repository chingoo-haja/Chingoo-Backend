package com.ldsilver.chingoohaja.dto.oauth.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.domain.common.enums.Provider;
import com.ldsilver.chingoohaja.validation.AuthValidationConstants;
import com.ldsilver.chingoohaja.validation.validator.DeviceInfo;
import com.ldsilver.chingoohaja.validation.validator.OAuthCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import static com.ldsilver.chingoohaja.validation.AuthValidationConstants.OAuth.MAX_STATE_LENGTH;
import static com.ldsilver.chingoohaja.validation.AuthValidationConstants.OAuth.MIN_STATE_LENGTH;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialLoginRequest {

    @NotBlank(message = AuthValidationConstants.OAuth.AUTH_CODE_REQUIRED)
    @OAuthCode(
            provider = Provider.ANY,
            message = AuthValidationConstants.OAuth.AUTH_CODE_INVALID_LENGTH
    )
    @JsonProperty("code")
    private String code;

    @NotBlank(message = AuthValidationConstants.OAuth.STATE_REQUIRED)
    @Size(min = MIN_STATE_LENGTH, max = MAX_STATE_LENGTH, message = AuthValidationConstants.OAuth.STATE_TOO_INVALID_LENGTH)
    @JsonProperty("state")
    private String state;

    @JsonProperty("code_verifier")
    @Size(
            min = AuthValidationConstants.OAuth.MIN_CODE_VERIFIER_LENGTH,
            max = AuthValidationConstants.OAuth.MAX_CODE_VERIFIER_LENGTH,
            message = AuthValidationConstants.OAuth.CODE_VERIFIER_INVALID_LENGTH
    )
    private String codeVerifier;

    @JsonProperty("redirect_uri")
    @Size(max = AuthValidationConstants.OAuth.MAX_REDIRECT_URI_LENGTH,
            message = AuthValidationConstants.OAuth.REDIRECT_URI_TOO_LONG)
    private String redirectUri;

    @DeviceInfo(
            nullable = true,
            message = AuthValidationConstants.Device.DEVICE_INFO_TOO_LONG
    )
    @JsonProperty("device_info")
    private String deviceInfo;

    // 서버에서 동적으로 설정되는 필드들 (검증 제외)
    @Setter
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String clientIp;



    private SocialLoginRequest(String code, String state, String deviceInfo, String codeVerifier, String redirectUri) {
        this.code = code;
        this.state = state;
        this.deviceInfo = deviceInfo;
        this.codeVerifier = codeVerifier;
        this.redirectUri = redirectUri;
    }

    public static SocialLoginRequest of(String code, String state) {
        return new SocialLoginRequest(code, state, null, null, null);
    }

    public static SocialLoginRequest of(String code, String state, String deviceInfo) {
        return new SocialLoginRequest(code, state, deviceInfo, null, null);
    }

    public static SocialLoginRequest of(String code, String state, String deviceInfo, String codeVerifier) {
        return new SocialLoginRequest(code, state, deviceInfo, codeVerifier, null);
    }

    public static SocialLoginRequest withPKCE(String code, String state, String codeVerifier) {
        return new SocialLoginRequest(code, state, null, codeVerifier, null);
    }

    public static SocialLoginRequest forTest(String code, String state) {
        return new SocialLoginRequest(code, state, "Test Device", null, null);
    }


    // 비즈니스 로직 메서드들
    public boolean hasDeviceInfo() {
        return deviceInfo != null && !deviceInfo.trim().isEmpty();
    }

    public boolean hasCodeVerifier() {
        return codeVerifier != null && !codeVerifier.trim().isEmpty();
    }

    public boolean hasRedirectUri() {
        return redirectUri != null && !redirectUri.trim().isEmpty();
    }

    public String getSafeDeviceInfo() {
        return hasDeviceInfo() ? deviceInfo : "Unknown Device";
    }

    public boolean usesPKCE() {
        return hasCodeVerifier();
    }

    public SocialLoginRequest withCodeVerifier(String codeVerifier) {
        this.codeVerifier = codeVerifier;
        return this;
    }

    public SocialLoginRequest withDeviceInfo(String deviceInfo) {
        this.deviceInfo = deviceInfo;
        return this;
    }

    public static Provider parseProvider(String providerString) {
        if (providerString == null || providerString.trim().isEmpty()) {
            throw new IllegalArgumentException("Provider cannot be null or empty");
        }

        try {
            return Provider.valueOf(providerString.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported provider: " + providerString, e);
        }
    }
}
