package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.dto.call.response.TokenResponse;
import com.ldsilver.chingoohaja.infrastructure.agora.AgoraTokenGenerator;
import com.ldsilver.chingoohaja.repository.CallRepository;
import com.ldsilver.chingoohaja.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
}
