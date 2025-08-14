package com.ldsilver.chingoohaja.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.firebase")
public class FirebaseProperties {

    @NotBlank(message = "Firebase 서비스 계정 파일 경로는 필수입니다.")
    private String serviceAccountPath;

    @NotBlank(message = "Firebase Storage 버킷명은 필수입니다.")
    private String storageBucket;
}
