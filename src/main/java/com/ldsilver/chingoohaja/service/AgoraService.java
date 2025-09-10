package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.infrastructure.agora.AgoraRestClient;
import com.ldsilver.chingoohaja.infrastructure.agora.AgoraTokenGenerator;
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
     * Agora 서비스 초기화 및 연결 테스트
     */
    public boolean initializeAndTest() {
        log.info("Agora 서비스 초기화 시작");

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

    /**
     * 채널용 RTC 토큰 생성
     */
    public String generateChannelToken(String channelName, Long userId) {
        validateInput(channelName, userId);

        // userId를 int로 변환 (Agora는 32bit 정수 사용)
        int agoraUid = userId.intValue();

        return tokenGenerator.generateRtcToken(channelName, agoraUid);
    }

    /**
     * 테스트용 토큰 생성
     */
    public String generateTestToken() {
        String testChannel = "test_channel_" + System.currentTimeMillis();
        return tokenGenerator.generateRtcToken(testChannel, 0);
    }



    private void validateInput(String channelName, Long userId) {
        if (channelName == null || channelName.trim().isEmpty()) {
            throw new IllegalArgumentException("채널명은 필수입니다.");
        }
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("사용자 ID는 양수여야 합니다.");
        }
        if (userId > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("사용자 ID가 너무 큽니다.");
        }
    }
}
