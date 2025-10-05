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
import com.ldsilver.chingoohaja.repository.CallRepository;
import com.ldsilver.chingoohaja.repository.CallSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final AgoraRecordingService agoraRecordingService;
    private final RecordingProperties recordingProperties;
    private final CallSessionRepository callSessionRepository;

    @Transactional
    public void startCall(Long callId) {
        log.debug("통화 시작 처리 - callId: {}", callId);

        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new CustomException(ErrorCode.CALL_NOT_FOUND));

        try {
            call.startCall();
            callRepository.save(call);

            // ✅ 녹음 설정 로그 추가
            log.info("녹음 자동 시작 설정: {}, 채널명: {}",
                    recordingProperties.isAutoStart(), call.getAgoraChannelName());

            if (recordingProperties.isAutoStart() && call.getAgoraChannelName() != null) {
                log.debug("통화 시작으로 인한 자동 녹음 시작 - callId: {}", callId);

                RecordingRequest recordingRequest = RecordingRequest.of(callId, call.getAgoraChannelName());
                agoraRecordingService.startRecording(recordingRequest);

                log.info("통화 및 자동 녹음 시작 완료 - callId: {}", callId);
            } else {
                log.info("통화 시작 완료 (녹음 없음) - callId: {}", callId);
            }
        } catch (Exception e) {
            log.error("통화 시작 처리 실패 - callId: {}", callId, e);
            if (call.getCallStatus() != CallStatus.IN_PROGRESS) {
                try {
                    call.startCall();
                    callRepository.save(call);
                    log.warn("녹음 실패했지만 통화는 시작됨 - callId: {}", callId);
                } catch (Exception startEx) {
                    log.error("통화, 녹음 시작 실패 - callId: {}", callId, startEx);
                    throw startEx;
                }
            }
        }
    }

    @Transactional
    public void endCall(Long callId) {
        log.debug("통화 종료 처리 - callId: {}", callId);

        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new CustomException(ErrorCode.CALL_NOT_FOUND));

        try {
            if (recordingProperties.isAutoStop() && call.isRecordingActive()) {
                log.debug("통화 종료로 인한 자동 녹음 중지 - callId: {}", callId);
                agoraRecordingService.autoStopRecordingOnCallEnd(callId);
            }

            updateAllSessionsToLeft(callId);

            call.endCall();
            callRepository.save(call);
            log.info("통화 종료 완료 - callId: {}", callId);
        } catch (Exception e) {
            log.error("통화 종료 처리 실패 - callId: {}", callId, e);

            // 녹음 중지가 실패해도 통화는 종료
            try {
                call.endCall();
                callRepository.save(call);
                log.warn("녹음 중지 실패했지만 통화는 종료됨 - callId: {}", callId);
            } catch (Exception saveEx) {
                log.error("통화 종료 상태 저장 실패 - callId: {}", callId, saveEx);
                throw saveEx;
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

    private void updateAllSessionsToLeft(Long callId) {
        try {
            LocalDateTime leftAt = LocalDateTime.now();
            int updatedCount = callSessionRepository.endAllSessionsForCall(callId, leftAt);

            if (updatedCount > 0) {
                log.info("모든 활성 세션을 LEFT로 업데이트 - callId: {}, updateCount: {}",
                        callId, updatedCount);
            } else {
                log.warn("일괄 업데이트 실패, 개별 업데이트 시도 - callId: {}", callId);
                updateSessionsIndividually(callId);
            }
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
