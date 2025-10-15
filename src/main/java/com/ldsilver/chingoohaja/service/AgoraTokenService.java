package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.CallSession;
import com.ldsilver.chingoohaja.dto.call.request.TokenRequest;
import com.ldsilver.chingoohaja.dto.call.response.BatchTokenResponse;
import com.ldsilver.chingoohaja.dto.call.response.TokenRenewResponse;
import com.ldsilver.chingoohaja.dto.call.response.TokenResponse;
import com.ldsilver.chingoohaja.infrastructure.agora.AgoraTokenGenerator;
import com.ldsilver.chingoohaja.repository.CallRepository;
import com.ldsilver.chingoohaja.repository.CallSessionRepository;
import com.ldsilver.chingoohaja.repository.UserRepository;
import com.ldsilver.chingoohaja.validation.CallValidationConstants;
import io.agora.media.RtcTokenBuilder2;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgoraTokenService {

    private final AgoraTokenGenerator agoraTokenGenerator;
    private final UserRepository userRepository;
    private final CallRepository callRepository;
    private final CallSessionRepository callSessionRepository;

    /**
     * - ✅ 매칭 완료 시: generateTokensForMatching() 사용 (배치 생성)
     * - ✅ 개별 토큰 필요시: generateTokenForCall() 사용 (권한 검증 포함)
     * - ✅ 일반적 용도: 기존 generateRtcToken() 유지
     */

    @Transactional
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

        try {
            Optional<CallSession> existingSession = callSessionRepository
                    .findActiveSessionByCallIdAndUserId(callId, userId);

            if (existingSession.isPresent()) {
                CallSession session = existingSession.get();


                log.info("기존 세션의 Token 반환 - userId: {}, callId: {}, agoraUid: {}, expiresAt: {}",
                        userId, callId, session.getAgoraUid(), session.getTokenExpiresAt());

                return TokenResponse.rtcOnly(
                        session.getRtcToken(),
                        channelName,
                        session.getAgoraUid(),
                        userId,
                        CallValidationConstants.DEFAULT_ROLE,
                        session.getTokenExpiresAt()
                );
            }

            // ✅ 2. 기존 세션이 없으면 새 토큰 생성
            Long agoraUid = generateAgoraUid(userId);

            String rtcToken = agoraTokenGenerator.generateRtcToken(
                    channelName,
                    safeLongToInt(agoraUid),
                    RtcTokenBuilder2.Role.ROLE_PUBLISHER,
                    CallValidationConstants.DEFAULT_TTL_SECONDS_ONE_HOURS
            );

            // ✅ 토큰 생성 시각 기준으로 만료 시각 계산
            LocalDateTime expiresAt = LocalDateTime.now()
                    .plusSeconds(CallValidationConstants.DEFAULT_TTL_SECONDS_ONE_HOURS);

            log.info("새 Token 생성 완료 - userId: {}, callId: {}, agoraUid: {}, expiresAt: {}",
                    userId, callId, agoraUid, expiresAt);

            return TokenResponse.rtcOnly(
                    rtcToken,
                    channelName,
                    agoraUid,
                    userId,
                    CallValidationConstants.DEFAULT_ROLE,
                    expiresAt // ✅ 방금 생성한 시각 기준
            );
        } catch (Exception e) {
            log.error("통화용 Token 생성 실패 - userId: {}, callId: {}", userId, callId, e);
            throw new CustomException(ErrorCode.CALL_SESSION_ERROR);
        }
    }

    @Transactional
    public BatchTokenResponse generateTokenForMatching(Call call) {
        log.debug("매칭 완료용 배치 Token 생성 - callId: {}", call.getId());

        String channelName = getOrCreateChannelName(call);

        Long user1Id = call.getUser1().getId();
        Long user2Id = call.getUser2().getId();

        try {
            CallSession user1Session = callSessionRepository
                    .findActiveSessionByCallIdAndUserId(call.getId(), user1Id)
                    .orElseThrow(() -> new CustomException(ErrorCode.CALL_SESSION_ERROR,
                            "User1의 활성 세션을 찾을 수 없습니다."));

            CallSession user2Session = callSessionRepository
                    .findActiveSessionByCallIdAndUserId(call.getId(), user2Id)
                    .orElseThrow(() -> new CustomException(ErrorCode.CALL_SESSION_ERROR,
                            "User2의 활성 세션을 찾을 수 없습니다."));


            TokenResponse user1TokenResponse = TokenResponse.rtcOnly(
                    user1Session.getRtcToken(),
                    channelName,
                    user1Session.getAgoraUid(),
                    user1Id,
                    CallValidationConstants.DEFAULT_ROLE,
                    user1Session.getTokenExpiresAt()
            );

            TokenResponse user2TokenResponse = TokenResponse.rtcOnly(
                    user2Session.getRtcToken(),
                    channelName,
                    user2Session.getAgoraUid(),
                    user2Id,
                    CallValidationConstants.DEFAULT_ROLE,
                    user2Session.getTokenExpiresAt()
            );

            log.info("매칭용 배치 Token 생성 완료 - callId: {}, channelName: {}, uids: [{}, {}]",
                    call.getId(), channelName, user1Session.getAgoraUid(), user2Session.getAgoraUid());

            return new BatchTokenResponse(user1TokenResponse, user2TokenResponse);
        } catch (CustomException e) {
            log.error("매칭용 배치 Token 생성 실패 - callId: {}", call.getId(), e);
            throw e;
        } catch (Exception e) {
            log.error("매칭용 배치 Token 생성 실패 - callId: {}", call.getId(), e);
            throw new CustomException(ErrorCode.CALL_SESSION_ERROR);
        }
    }

    public TokenResponse generateRtcToken(TokenRequest request) {
        log.debug("일반 RTC Token 생성 - channelName: {}, userId: {}",
                request.channelName(), request.userId());

        if (!userRepository.existsById(request.userId())) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }

        Long finalAgoraUid = determineAgoraUid(request.agoraUid(), request.userId());
        RtcTokenBuilder2.Role role = request.isPublisher() ?
                RtcTokenBuilder2.Role.ROLE_PUBLISHER : RtcTokenBuilder2.Role.ROLE_SUBSCRIBER;

        try {
            String rtcToken = agoraTokenGenerator.generateRtcToken(
                    request.channelName(),
                    safeLongToInt(finalAgoraUid),
                    role,
                    request.expirationSeconds()
            );

            // 나중에 rtmToken이 필요하면 request.needsRtmToken() 활용해 생성

            LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(request.expirationSeconds());

            return TokenResponse.of(
                    rtcToken, null, request.channelName(),
                    finalAgoraUid,
                    request.userId(),
                    request.role(),
                    expiresAt
            );

        } catch (Exception e) {
            log.error("일반 RTC Token 생성 실패 - userId: {}, channel: {}",
                    request.userId(), request.channelName(), e);
            throw new CustomException(ErrorCode.CALL_SESSION_ERROR);
        }

    }

    @Transactional
    public TokenRenewResponse renewRtcToken(Long userId, Long callId) {
        log.debug("RTC Token 갱신 시작 - userId: {}, callId: {}", userId, callId);

        // 1. Call 조회 및 권한 검증
        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new CustomException(ErrorCode.CALL_NOT_FOUND));

        if (!call.isParticipant(userId)) {
            log.warn("Token 갱신 권한 없음 - userId: {}, callId: {}", userId, callId);
            throw new CustomException(ErrorCode.CALL_NOT_PARTICIPANT);
        }

        // 2. 통화 상태 확인 (진행 중인 통화만 갱신 가능)
        if (!call.isInProgress()) {
            log.warn("진행 중이지 않은 통화의 토큰 갱신 시도 - callId: {}, status: {}",
                    callId, call.getCallStatus());
            throw new CustomException(ErrorCode.CALL_NOT_IN_PROGRESS);
        }

        // 3. 활성 CallSession 조회
        CallSession session = callSessionRepository
                .findActiveSessionByCallIdAndUserId(callId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.CALL_SESSION_ERROR,
                        "활성 세션을 찾을 수 없습니다."));

        // 4. 채널명 확인
        String channelName = call.getAgoraChannelName();
        if (channelName == null || channelName.trim().isEmpty()) {
            log.error("채널명이 없음 - callId: {}", callId);
            throw new CustomException(ErrorCode.CALL_SESSION_ERROR, "채널 정보가 없습니다.");
        }

        // 5. 기존 Agora UID 사용 (변경하면 안 됨!)
        Long agoraUid = session.getAgoraUid();
        if (agoraUid == null) {
            log.error("Agora UID가 없음 - sessionId: {}", session.getId());
            throw new CustomException(ErrorCode.CALL_SESSION_ERROR, "세션 UID 정보가 없습니다.");
        }

        // 6. 새로운 RTC Token 생성
        String newRtcToken = agoraTokenGenerator.generateRtcToken(
                channelName,
                safeLongToInt(agoraUid),
                RtcTokenBuilder2.Role.ROLE_PUBLISHER,
                CallValidationConstants.DEFAULT_TTL_SECONDS_ONE_HOURS // 1시간
        );

        // 7. 새로운 만료 시각 계산
        LocalDateTime newExpiresAt = LocalDateTime.now()
                .plusSeconds(CallValidationConstants.DEFAULT_TTL_SECONDS_ONE_HOURS);

        // 8. CallSession 업데이트
        session.renewToken(newRtcToken, newExpiresAt);
        callSessionRepository.save(session);

        log.info("RTC Token 갱신 완료 - userId: {}, callId: {}, sessionId: {}, newExpiresAt: {}",
                userId, callId, session.getId(), newExpiresAt);

        return TokenRenewResponse.of(newRtcToken, newExpiresAt);
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
        if (userId == null || userId < 0) {
            throw new CustomException(ErrorCode.AGORA_UID_INVALID);
        }

        if (userId <= CallValidationConstants.AGORA_MAX_UID) {
            return userId;
        } else {
            throw new CustomException(ErrorCode.USER_ID_TOO_LARGE, userId);
        }
    }

    private int safeLongToInt(Long longValue) {
        if (longValue == null) {
            return 0; // Agora가 자동 할당
        }

        if (longValue < 0) {
            throw new CustomException(ErrorCode.AGORA_UID_INVALID,
                    "Agora UID는 음수일 수 없습니다: " + longValue);
        }

        if (longValue > CallValidationConstants.AGORA_MAX_UID) {
            throw new CustomException(ErrorCode.AGORA_UID_INVALID,
                    "Agora UID가 최대값을 초과했습니다: " + longValue + " > " + CallValidationConstants.AGORA_MAX_UID);
        }

        return (int) (longValue & 0xFFFF_FFFFL);
    }

    private Long determineAgoraUid(Long requestedUid, Long userId) {
        if (requestedUid != null && requestedUid > 0) {
            return requestedUid;
        }

        if (requestedUid != null && requestedUid < 0) {
            throw new CustomException(ErrorCode.AGORA_UID_INVALID, "Agora UID는 음수일 수 없습니다: " + requestedUid);
        }
        return generateAgoraUid(userId);
    }

}
