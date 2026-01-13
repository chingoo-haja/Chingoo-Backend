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

import java.util.List;

/**
 * CallStatusService: 채널 참가/퇴장 시 세션 상태 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallStatusService {

    private final CallRepository callRepository;
    private final CallService callService;
    private final EvaluationService evaluationService;

    @Transactional(readOnly = true)
    public CallStatusResponse getCallStatus(Long callId, Long userId) {
        log.debug("통화 상태 조회 - callId: {}, userId: {}", callId, userId);

        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new CustomException(ErrorCode.CALL_NOT_FOUND));

        if (!call.isParticipant(userId)) {
            throw new CustomException(ErrorCode.CALL_NOT_PARTICIPANT);
        }

        CallStatusResponse baseResponse = CallStatusResponse.from(call, userId);

        // 평가 정보 추가 (완료된 통화의 경우에만)
        if (call.getCallStatus() == CallStatus.COMPLETED) {
            boolean canEvaluate = evaluationService.canEvaluate(userId, callId);
            boolean hasEvaluated = evaluationService.hasUserEvaluatedCall(userId, callId);

            return baseResponse.withEvaluationInfo(canEvaluate, hasEvaluated);
        }

        return baseResponse.withEvaluationInfo(false, false);
    }

    @Transactional
    public CallStatusResponse endCall(Long callId, Long userId) {
        log.debug("통화 종료 - callId: {}, userId: {}", callId, userId);

        if (userId == null) {
            log.error("통화 종료 실패: userId가 null입니다.");
            throw new CustomException(ErrorCode.UNAUTHORIZED, "인증 정보가 없습니다.");
        }

        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new CustomException(ErrorCode.CALL_NOT_FOUND));

        if (!call.isParticipant(userId)) {
            throw new CustomException(ErrorCode.CALL_NOT_PARTICIPANT);
        }

        if (call.getCallStatus() == CallStatus.COMPLETED) {
            log.info("이미 종료된 통화 - callId: {}, userId: {}", callId, userId);
            CallStatusResponse baseResponse = CallStatusResponse.from(call, userId);
            boolean canEvaluate = evaluationService.canEvaluate(userId, callId);
            boolean hasEvaluated = evaluationService.hasUserEvaluatedCall(userId, callId);
            return baseResponse.withEvaluationInfo(canEvaluate, hasEvaluated);
        }


        if (call.getCallStatus() == CallStatus.CANCELLED ||
                call.getCallStatus() == CallStatus.FAILED) {
            throw new CustomException(ErrorCode.CALL_ALREADY_ENDED);
        }

        callService.endCall(callId);

        Call updatedCall = callRepository.findById(callId)
                .orElseThrow(() -> new CustomException(ErrorCode.CALL_NOT_FOUND));

        log.info("통화 종료 완료 - callId: {}, userId: {}, duration: {}초",
                callId, userId, updatedCall.getDurationSeconds());

        CallStatusResponse baseResponse = CallStatusResponse.from(updatedCall, userId);

        boolean canEvaluate = evaluationService.canEvaluate(userId, callId);
        return baseResponse.withEvaluationInfo(canEvaluate, false);
    }

    @Transactional(readOnly = true)
    public CallStatusResponse getActiveCallByUserId (Long userId) {
        log.debug("활성 통화 조회 - userId: {}", userId);

        List<Call> activeCalls = callRepository.findActiveCallsByUserId(userId);

        if (activeCalls.isEmpty()) {
            throw new CustomException(ErrorCode.CALL_NOT_FOUND);
        }

        Call activeCall = activeCalls.get(0);
        CallStatusResponse baseResponse = CallStatusResponse.from(activeCall, userId);

        return baseResponse.withEvaluationInfo(false, false);
    }
}
