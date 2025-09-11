package com.ldsilver.chingoohaja.infrastructure.agora;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.config.AgoraProperties;
import com.ldsilver.chingoohaja.validation.CallValicationConstants;
import io.agora.media.RtcTokenBuilder2;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class AgoraTokenGenerator {

    private final AgoraProperties agoraProperties;

    /**
     * RTC Token 생성 (음성 통화용)
     */
    public String generateRtcToken(String channelName, int uid, RtcTokenBuilder2.Role role, int expirationTimeInSeconds) {
        validateChannelName(channelName);
        if (uid < 0) {
            throw new CustomException(ErrorCode.valueOf(CallValicationConstants.Token.UID_NOT_MINUS));
        }
        if (role == null) {
            throw new CustomException(ErrorCode.valueOf(CallValicationConstants.Token.ROLE_REQUIRED));
        }
        if (expirationTimeInSeconds <= 0) {
            throw new CustomException(ErrorCode.valueOf(CallValicationConstants.Token.INVALID_EXPIRED_TIME));
        }

        try {
            RtcTokenBuilder2 tokenBuilder = new RtcTokenBuilder2();
            String token = tokenBuilder.buildTokenWithUid(
                    agoraProperties.getAppId(),
                    agoraProperties.getAppCertificate(),
                    channelName,
                    uid,
                    role,
                    expirationTimeInSeconds,
                    expirationTimeInSeconds
            );

            log.debug("RTC Token 생성 완료 - channel: {}, uid: {}, role: {}",
                    channelName, uid, role);

            return token;
        } catch (Exception e) {
            log.error("RTC Token 생성 실패 - channel: {}, uid: {}", channelName, uid, e);
            throw new RuntimeException("RTC Token 생성에 실패했습니다.", e);
        }
    }

    /**
     * 기본 설정으로 RTC Token 생성
     */
    public String generateRtcToken(String channelName, int uid) {
        return generateRtcToken(channelName, uid,
                RtcTokenBuilder2.Role.ROLE_PUBLISHER,
                agoraProperties.getTokenExpirationInSeconds());
    }


    private void validateChannelName(String channelName) {
        if (channelName == null || channelName.trim().isEmpty()) {
            throw new IllegalArgumentException("채널명은 필수입니다.");
        }
        if (channelName.length() > 64) {
            throw new IllegalArgumentException("채널명은 64자를 초과할 수 없습니다.");
        }
        if (!channelName.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException("채널명은 영문, 숫자, 언더스코어, 하이픈만 사용 가능합니다.");
        }
    }

}
