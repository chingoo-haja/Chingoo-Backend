package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.dto.call.response.TokenResponse;
import com.ldsilver.chingoohaja.infrastructure.agora.AgoraTokenGenerator;
import com.ldsilver.chingoohaja.repository.CallRepository;
import com.ldsilver.chingoohaja.repository.UserRepository;
import io.agora.media.RtcTokenBuilder2;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgoraTokenService {

    private final AgoraTokenGenerator agoraTokenGenerator;
    private final UserRepository userRepository;
    private final CallRepository callRepository;

    public TokenResponse generateTokenForCall(Long userId, Long callId) {
        log.debug("통화용 Token 생성 - userId: {}, callId: {}", userId, callId);

        if (!userRepository.existsById(userId)) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }

        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new CustomException(ErrorCode.CALL_NOT_FOUND));
        if (!call.isParticipant(userId)) {
            throw new CustomException(ErrorCode.CALL_NOT_PARTICIPANT);
        }

        String channelName = getOrCreateChannelName(call);

        Long agoraUid = generateAgoraUid(userId);

        try {
            String rtcToken  = agoraTokenGenerator.generateRtcToken(
                    channelName,
                    safeLongToInt(agoraUid),
                    RtcTokenBuilder2.Role.ROLE_PUBLISHER,
                    3600
            );

            LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(3600);

            log.info("통화용 Token 생성 완료 - userId: {}, callId: {}, agoraUid: {}",
                    userId, callId, agoraUid);

            return TokenResponse.rtcOnly(
                    rtcToken,
                    channelName,
                    agoraUid,
                    userId,
                    "PUBLISHER",
                    expiresAt
            );
        } catch (Exception e) {
            log.error("통화용 Token 생성 실패 - userId: {}, callId: {}", userId, callId, e);
            throw new CustomException(ErrorCode.CALL_SESSION_ERROR, "토큰 생성 중 오류가 발생했습니다.");
        }
    }

    private String getOrCreateChannelName(Call call) {
        if (call.getAgoraChannelName() != null && !call.getAgoraChannelName().trim().isEmpty()) {
            return call.getAgoraChannelName();
        }

        String channelName = "call_" + call.getId() + "_" + System.currentTimeMillis();
        call.setAgoraChannelInfo(channelName);
        callRepository.save(call);

        log.debug("새 채널 생성 - callId: {}, channelName: {}", call.getId(), channelName);
        return channelName;
    }

    private Long generateAgoraUid(Long userId) {
        if (userId <= 0) {
            throw new CustomException(ErrorCode.AGORA_UID_INVALID);
        }

        final long AGORA_MAX_UID = 4_294_967_295L;

        if (userId <= AGORA_MAX_UID) {
            return userId;
        } else {
            throw new CustomException(ErrorCode.USER_ID_TOO_LARGE, userId);
        }
    }

    private int safeLongToInt(Long longValue) {
        if (longValue == null) {
            return 0; // Agora가 자동 할당
        }

        final long AGORA_MAX_UID = 4_294_967_295L;

        if (longValue < 0) {
            throw new CustomException(ErrorCode.AGORA_UID_INVALID,
                    "Agora UID는 음수일 수 없습니다: " + longValue);
        }

        if (longValue > AGORA_MAX_UID) {
            throw new CustomException(ErrorCode.AGORA_UID_INVALID,
                    "Agora UID가 최대값을 초과했습니다: " + longValue + " > " + AGORA_MAX_UID);
        }

        return longValue.intValue();
    }

}
