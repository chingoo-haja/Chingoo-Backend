package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.enums.CallStatus;
import com.ldsilver.chingoohaja.dto.call.response.CallStatusResponse;
import com.ldsilver.chingoohaja.repository.CallRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallStatusService {

    private final CallRepository callRepository;
    private final CallService callService;

    @Transactional(readOnly = true)
    public CallStatusResponse getCallStatus(Long callId, Long userId) {
        log.debug("통화 상태 조회 - callId: {}, userId: {}", callId, userId);

        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new CustomException(ErrorCode.CALL_NOT_FOUND));

        if (!call.isParticipant(userId)) {
            throw new CustomException(ErrorCode.CALL_NOT_PARTICIPANT);
        }

        return CallStatusResponse.from(call, userId);
    }

    @Transactional
    public CallStatusResponse endCall(Long callId, Long userId) {
        log.debug("통화 종료 - callId: {}, userId: {}", callId, userId);

        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new CustomException(ErrorCode.CALL_NOT_FOUND));

        if (!call.isParticipant(userId)) {
            throw new CustomException(ErrorCode.CALL_NOT_PARTICIPANT);
        }

        if (call.getCallStatus() == CallStatus.COMPLETED ||
                call.getCallStatus() == CallStatus.CANCELLED ||
                call.getCallStatus() == CallStatus.FAILED) {
            throw new CustomException(ErrorCode.CALL_ALREADY_ENDED);
        }

        callService.endCall(callId);

        Call updatedCall = callRepository.findById(callId)
                .orElseThrow(() -> new CustomException(ErrorCode.CALL_NOT_FOUND));

        log.info("통화 종료 완료 - callId: {}, userId: {}, duration: {}초",
                callId, userId, updatedCall.getDurationSeconds());

        return CallStatusResponse.from(updatedCall, userId);
    }
}
