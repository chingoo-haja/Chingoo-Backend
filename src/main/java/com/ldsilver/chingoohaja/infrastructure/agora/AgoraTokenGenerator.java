package com.ldsilver.chingoohaja.infrastructure.agora;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.config.AgoraProperties;
import com.ldsilver.chingoohaja.validation.CallValidationConstants;
import io.agora.media.RtcTokenBuilder2;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

import static com.ldsilver.chingoohaja.validation.CallValidationConstants.CHANNEL_NAME_PATTERN;

@Slf4j
@RequiredArgsConstructor
@Component
public class AgoraTokenGenerator {

    private final AgoraProperties agoraProperties;

    /**
     * RTC Token 생성 (음성 통화용)
     */
    public String generateRtcToken(String channelName, int uid, RtcTokenBuilder2.Role role, int expirationTimeInSeconds) {
        validateChannelName(channelName);
        if (uid < 0) {
            throw new CustomException(ErrorCode.UID_NOT_MINUS);
        }
        if (role == null) {
            throw new CustomException(ErrorCode.ROLE_REQUIRED);
        }
        if (expirationTimeInSeconds <= 0) {
            throw new CustomException(ErrorCode.INVALID_EXPIRED_TIME);
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
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "RTC Token 생성에 실패했습니다.", e);
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
            throw new CustomException(ErrorCode.CHANNEL_NAME_REQUIRED);
        }
        if (channelName.getBytes(StandardCharsets.UTF_8).length > CallValidationConstants.CHANNEL_NAME_MAX_BYTES) {
            throw new CustomException(ErrorCode.CHANNEL_NAME_TOO_LONG);
        }
        if (!CHANNEL_NAME_PATTERN.matcher(channelName).matches()) {
            throw new CustomException(ErrorCode.CHANNEL_NAME_INVALID);
        }
    }

}
