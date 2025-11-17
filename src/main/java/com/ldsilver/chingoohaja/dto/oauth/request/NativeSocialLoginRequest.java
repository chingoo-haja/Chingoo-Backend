package com.ldsilver.chingoohaja.dto.oauth.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.validation.validator.DeviceInfo;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NativeSocialLoginRequest {

    @NotBlank(message = "카카오 액세스 토큰은 필수입니다.")
    @JsonProperty("kakao_access_token")
    private String kakaoAccessToken;

    @DeviceInfo(nullable = true, message = "디바이스 정보 형식이 올바르지 않습니다.")
    @JsonProperty("device_info")
    private String deviceInfo;

    // 클라이언트 IP는 서버에서 설정
    private String clientIp;

    public NativeSocialLoginRequest() {
    }

    public NativeSocialLoginRequest(String kakaoAccessToken, String deviceInfo) {
        this.kakaoAccessToken = kakaoAccessToken;
        this.deviceInfo = deviceInfo;
    }

    public String getSafeDeviceInfo() {
        return deviceInfo != null && !deviceInfo.trim().isEmpty()
                ? deviceInfo : "Unknown Device";
    }

    public boolean hasDeviceInfo() {
        return deviceInfo != null && !deviceInfo.trim().isEmpty();
    }
}