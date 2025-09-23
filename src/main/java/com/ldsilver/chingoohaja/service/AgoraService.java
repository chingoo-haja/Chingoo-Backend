package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.dto.call.AgoraHealthStatus;
import com.ldsilver.chingoohaja.infrastructure.agora.AgoraRestClient;
import com.ldsilver.chingoohaja.infrastructure.agora.AgoraTokenGenerator;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgoraService {

    private final AgoraTokenGenerator tokenGenerator;
    private final AgoraRestClient restClient;

    /**
     * 애플리케이션 시작 시 Agora 서비스 초기화
     */
    @PostConstruct
    public void initializeOnStartup() {
        log.info("애플리케이션 시작 - Agora 서비스 초기화");
        boolean result = initializeAndTest();

        if (result) {
            log.info("Agora 서비스 초기화 성공 - 모든 기능 사용 가능");
        } else {
            log.warn("Agora 일부 기능에 문제가 있지만 애플리케이션은 계속 실행됩니다.");
        }
    }

    /**
     * Agora 서비스 초기화 및 연결 테스트
     */
    public boolean initializeAndTest() {
        try {
            // 토큰 생성 테스트
            String testToken = generateTestToken();
            boolean tokenGenerated = testToken != null && !testToken.isEmpty();

            if (!tokenGenerated) {
                log.error("Agora 토큰 생성 실패 - 앱 설정 확인 필요");
                return false;
            }

            // REST API 연결 테스트
            Boolean connectionResult = restClient.testConnection().block();

            if (Boolean.TRUE.equals(connectionResult)) {
                log.info("Agora 서비스 초기화 완료 - API 연결 성공");
                return true;
            } else {
                log.warn("Agora 서비스 초기화 완료 - API 연결 실패 (토큰 생성은 가능)");
                return false;
            }
        } catch (Exception e) {
            log.error("Agora 서비스 초기화 실패", e);
            return false;
        }
    }

    /**
     * 헬스체크 - Agora 서비스 상태 확인
     */
    public AgoraHealthStatus checkHealth() {
        try {
            // 1. 토큰 생성 테스트
            String testToken = generateTestToken();
            boolean tokenAvailable = testToken != null && !testToken.isEmpty();

            // 2. REST API 연결 테스트
            Boolean apiConnected = restClient.testConnection().block();
            boolean restApiAvailable = Boolean.TRUE.equals(apiConnected);

            // 3. 전체 상태 판단
            boolean isHealthy = tokenAvailable; // 토큰만 되면 기본 기능은 OK
            String statusMessage = buildStatusMessage(tokenAvailable, restApiAvailable);

            return AgoraHealthStatus.builder()
                    .isHealthy(isHealthy)
                    .tokenGenerationAvailable(tokenAvailable)
                    .restApiAvailable(restApiAvailable)
                    .statusMessage(statusMessage)
                    .checkedAt(java.time.LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Agora 헬스체크 실패", e);
            return AgoraHealthStatus.builder()
                    .isHealthy(false)
                    .tokenGenerationAvailable(false)
                    .restApiAvailable(false)
                    .statusMessage("Agora 서비스 점검 중 오류 발생: " + e.getMessage())
                    .errorMessage(e.getMessage())
                    .checkedAt(java.time.LocalDateTime.now())
                    .build();
        }
    }


    private String buildStatusMessage(boolean tokenAvailable, boolean restApiAvailable) {
        if (tokenAvailable && restApiAvailable) {
            return "모든 Agora 서비스가 정상 작동 중입니다.";
        } else if (tokenAvailable && !restApiAvailable) {
            return "기본 통화 기능은 정상이나, Cloud Recording 등 고급 기능이 제한될 수 있습니다.";
        } else if (!tokenAvailable && restApiAvailable) {
            return "토큰 생성에 문제가 있어 통화 기능을 사용할 수 없습니다.";
        } else {
            return "Agora 서비스에 전체적인 문제가 발생했습니다.";
        }
    }

    private String generateTestToken() {
        try {
            String testChannel = "health_check_" + System.currentTimeMillis();
            return tokenGenerator.generateRtcToken(testChannel, 0);
        } catch (Exception e) {
            log.error("테스트 토큰 생성 실패", e);
            return null;
        }
    }

}
