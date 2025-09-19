package com.ldsilver.chingoohaja.config;

import com.ldsilver.chingoohaja.validation.CallValidationConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.agora")
public class AgoraProperties {

    @NotBlank(message = "Agora App ID는 필수입니다.")
    @Size(min = 32, max = 32, message = "Agora App ID는 32자여야 합니다.")
    private String appId;

    @NotBlank(message = "Agora App Certificate는 필수입니다.")
    @Size(min = 32, max = 32, message = "Agora App Certificate는 32자여야 합니다.")
    private String appCertificate;

    @NotBlank(message = "Agora Customer ID는 필수입니다.")
    private String customerId;

    @NotBlank(message = "Agora Customer Secret은 필수입니다.")
    private String customerSecret;

    // Token 설정
    @Min(1)
    @Max(86400) // 24h
    private int tokenExpirationInSeconds = CallValidationConstants.DEFAULT_TTL_SECONDS; // 1시간

    // REST API 설정
    private String restApiBaseUrl = "https://api.agora.io";

    // Cloud Recording 설정
    private String recordingRegion = "AP"; // Asia Pacific
    private boolean useCustomStorage = false; // 커스텀 저장소 사용 여부
    private String recordingStorageVendor = "1"; // AWS S3
    private String recordingStorageBucket;
    private String recordingStorageAccessKey;
    private String recordingStorageSecretKey;

    public boolean isCloudRecordingConfigured() {
        // 기본적으로 Agora 저장소 사용 (항상 true)
        return appId != null && !appId.isBlank()
                && appCertificate != null && !appCertificate.isBlank()
                && customerId != null && !customerId.isBlank()
                && customerSecret != null && !customerSecret.isBlank();
    }

    public boolean useCustomStorage() {
        return useCustomStorage &&
                recordingStorageBucket != null && !recordingStorageBucket.trim().isEmpty() &&
                recordingStorageAccessKey != null && !recordingStorageAccessKey.trim().isEmpty() &&
                recordingStorageSecretKey != null && !recordingStorageSecretKey.trim().isEmpty();
    }
}
