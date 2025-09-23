package com.ldsilver.chingoohaja.service;

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

}
