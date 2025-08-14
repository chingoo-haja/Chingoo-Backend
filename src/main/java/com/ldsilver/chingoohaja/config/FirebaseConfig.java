package com.ldsilver.chingoohaja.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class FirebaseConfig {

    private final FirebaseProperties firebaseProperties;

    @PostConstruct
    public void initialize() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                ClassPathResource serviceAccount = new ClassPathResource(firebaseProperties.getServiceAccountPath());

                try (java.io.InputStream is = serviceAccount.getInputStream()){
                    FirebaseOptions options = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(is))
                            .setStorageBucket(firebaseProperties.getStorageBucket())
                            .build();
                    FirebaseApp.initializeApp(options);
                    log.info("Firebase 초기화 완료 - bucket: {}", firebaseProperties.getStorageBucket());
                }

            }
        } catch (IOException e) {
            log.error("Firebase 초기화 실패", e);
            throw new RuntimeException("Firebase 초기화에 실패했습니다.", e);
        }
    }
}
