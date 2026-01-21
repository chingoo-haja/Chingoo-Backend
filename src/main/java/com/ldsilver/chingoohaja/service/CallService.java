package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.config.RecordingProperties;
import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.CallSession;
import com.ldsilver.chingoohaja.domain.call.enums.CallStatus;
import com.ldsilver.chingoohaja.domain.call.enums.CallType;
import com.ldsilver.chingoohaja.domain.category.Category;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.dto.call.request.RecordingRequest;
import com.ldsilver.chingoohaja.event.CallStartedEvent;
import com.ldsilver.chingoohaja.repository.CallRecordingRepository;
import com.ldsilver.chingoohaja.repository.CallRepository;
import com.ldsilver.chingoohaja.repository.CallSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CallService : Call 엔티티의 생명주기 관리
 * 매칭 완료 → CallService.createCallFromMatching()
 * 통화 시작 → CallService.startCall() → 자동 녹음 시작
 * 통화 종료 → CallService.endCall() → 자동 녹음 중지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallService {

    private final CallRepository callRepository;
    private final CallRecordingRepository callRecordingRepository;
    private final AgoraRecordingService agoraRecordingService;
    private final RecordingProperties recordingProperties;
    private final CallSessionRepository callSessionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void startCall(Long callId) {
        log.debug("통화 시작 처리 - callId: {}", callId);

        Call call = callRepository.findByIdWithLock(callId)
                .orElseThrow(() -> new CustomException(ErrorCode.CALL_NOT_FOUND));

        if (call.getCallStatus() == CallStatus.IN_PROGRESS) {
            log.warn("이미 시작된 통화 - callId: {}, 중복 시작 요청 무시", callId);
            return;
        }

        if (call.getCallStatus() != CallStatus.READY) {
            log.error("통화 시작 불가 상태 - callId: {}, status: {}",
                    callId, call.getCallStatus());
            throw new CustomException(ErrorCode.CALL_START_FAILED,
                    "현재 상태에서는 통화를 시작할 수 없습니다: " + call.getCallStatus());
        }

        try {
            call.startCall(); //상태 변경
            callRepository.save(call);

            // 녹음 설정 로그 추가
            log.info("통화 시작 완료 - callId: {}, status: {}", callId, call.getCallStatus());

//            if (shouldStartRecording(call)) {
//                startRecordingAsync(call);
//            } else {
//                log.info("자동 녹음 비활성화 - callId: {}", callId);
//            }

            if (recordingProperties.isAutoStart() && call.getAgoraChannelName() != null) {
                eventPublisher.publishEvent(new CallStartedEvent(
                        call.getId(),
                        call.getAgoraChannelName()
                ));
                log.debug("CallStartedEvent 발행 완료 - callId: {}", callId);
            }
        } catch (CustomException ce) {
            // Call 엔티티에서 발생한 비즈니스 예외
            log.error("통화 시작 비즈니스 로직 실패 - callId: {}", callId, ce);
            throw ce;
        } catch (Exception e) {
            log.error("통화 시작 처리 실패 - callId: {}", callId, e);

            if (call.getCallStatus() == CallStatus.IN_PROGRESS) {
                log.warn("녹음 실패했지만 통화는 시작됨 - callId: {}", callId);
                return;
            }

            throw new CustomException(ErrorCode.CALL_START_FAILED, "통화 시작 실패", e);
        }
    }

    @Transactional
    public void endCall(Long callId) {
        log.debug("통화 종료 처리 - callId: {}", callId);

        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new CustomException(ErrorCode.CALL_NOT_FOUND));

        if (call.getCallStatus() == CallStatus.COMPLETED) {
            log.warn("이미 종료된 통화 - callId: {}, 중복 종료 요청 무시", callId);
            return;
        }

        if (call.getCallStatus() == CallStatus.CANCELLED ||
                call.getCallStatus() == CallStatus.FAILED) {
            log.warn("종료할 수 없는 상태 - callId: {}, status: {}",
                    callId, call.getCallStatus());
            return;
        }

        try {
            if (shouldStopRecording(callId)) {
                try {
                    stopRecordingAsync(callId);
                } catch (Exception re) {
                    log.warn("녹음 중지 실패 (통화 종료는 계속) - callId: {}", callId, re);
                }
            }

            try {
                updateAllSessionsToLeft(callId);
            } catch (Exception se) {
                log.warn("세션 상태 업데이트 실패 (통화 종료는 계속) - callId: {}", callId, se);
            }

            call.endCall();
            callRepository.save(call);

            log.info("통화 종료 완료 - callId: {}, duration: {}초",
                    callId, call.getDurationSeconds());

        } catch (CustomException ce) {
            log.error("통화 종료 비즈니스 로직 실패 - callId: {}", callId, ce);
            throw ce;
        } catch (Exception e) {
            log.error("통화 종료 처리 실패 - callId: {}", callId, e);

            // 녹음 중지가 실패해도 통화는 종료
            try {
                call.endCall();
                callRepository.save(call);
                log.warn("녹음 중지 실패했지만 통화는 종료됨 - callId: {}", callId);
            } catch (Exception saveEx) {
                log.error("통화 종료 상태 저장도 실패 - callId: {}", callId, saveEx);
                throw new CustomException(ErrorCode.CALL_SESSION_ERROR,
                        "통화 종료 처리 완전 실패", saveEx);
            }
        }
    }

    @Transactional
    public Call createCallFromMatching(User user1, User user2, Category category) {
        log.debug("매칭 완료 후 통화 생성 - users: [{}, {}], category: {}",
                user1.getId(), user2.getId(), category.getName());

        try {
            Call call = Call.from(user1, user2, category, CallType.RANDOM_MATCH);
            Call savedCall = callRepository.save(call);

            startCall(savedCall.getId());

            return savedCall;
        } catch (Exception e) {
            log.error("매칭 후 통화 생성 실패 - users: [{}, {}]", user1.getId(), user2.getId());
            throw e;
        }
    }

    private boolean shouldStartRecording(Call call) {
        if (!recordingProperties.isAutoStart()) {
            log.debug("자동 녹음 비활성화 - callId: {}", call.getId());
            return false;
        }

        if (call.getAgoraChannelName() == null || call.getAgoraChannelName().trim().isEmpty()) {
            log.warn("채널명 없음 - 녹음 시작 불가 - callID: {}", call.getId());
            return false;
        }

        if (callRecordingRepository.findByCallId(call.getId()).isPresent()) {
            log.warn("이미 녹음 진행 중 - callId: {}", call.getId());
            return false;
        }
        return true;
    }


    private boolean shouldStopRecording(Long callId) {
        if (!recordingProperties.isAutoStop()) {
            log.debug("자동 녹음 중지 비활성화 - callId: {}", callId);
            return false;
        }

        boolean hasActiveRecording = callRecordingRepository.findByCallId(callId).isPresent();

        if (!hasActiveRecording) {
            log.debug("진행 중인 녹음 없음 - callId: {}", callId);
            return false;
        }
        return true;
    }

    private void startRecordingAsync(Call call) {
        try {
            log.info("자동 녹음 시작 요청 - callId: {}, channel: {}",
                    call.getId(), call.getAgoraChannelName());

            RecordingRequest recordingRequest = RecordingRequest.of(
                    call.getId(),
                    call.getAgoraChannelName()
            );

            agoraRecordingService.startRecording(recordingRequest);
        } catch (Exception e) {
            log.error("자동 녹음 시작 실패 (통화는 정상 진행) - callId: {}", call.getId(), e);
        }
    }

    private void stopRecordingAsync(Long callId) {
        try{
            log.info("자동 녹음 중지 요청 - callId: {}", callId);
            agoraRecordingService.autoStopRecordingOnCallEnd(callId);
        } catch (Exception e) {
            log.error("자동 녹음 중지 실패 (통화 종료는 정상 진행) - callId: {}", callId, e);
        }
    }

    private void updateAllSessionsToLeft(Long callId) {
        try {
            LocalDateTime leftAt = LocalDateTime.now();
            int updatedCount = callSessionRepository.endAllSessionsForCall(callId, leftAt);

            if (updatedCount > 0) {
                log.info("모든 활성 세션 일괄 업데이트 완료 - callId: {}, updateCount: {}",
                        callId, updatedCount);
                return;
            }

            log.warn("일괄 업데이트 실패, 개별 업데이트 시도 - callId: {}", callId);
            updateSessionsIndividually(callId);

        } catch (Exception e) {
            log.error("세션 일괄 업데이트 실패, 개별 업데이트 재시도 - callId: {}", callId, e);

            try {
                updateSessionsIndividually(callId);
            } catch (Exception retryEx) {
                log.error("개별 세션 업데이트도 실패 - callId: {}", callId, retryEx);
            }
        }
    }

    private void updateSessionsIndividually(Long callId) {
        List<CallSession> joinedSessions = callSessionRepository.findJoinedSessionsByCallId(callId);

        log.info("개별 세션 업데이트 시작 - callId: {}, 대상 세션 수: {}", callId, joinedSessions.size());

        for (CallSession session : joinedSessions) {
            try {
                session.leaveSession();
                callSessionRepository.save(session);
                log.info("세션 개별 업데이트: JOINED -> LEFT - callId: {}, userId: {}, sessionId: {}",
                        callId, session.getUser().getId(), session.getId());
            } catch (Exception e) {
                log.error("세션 개별 업데이트 실패 - sessionId: {}", session.getId(), e);
            }
        }
    }
}
