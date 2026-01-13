package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.CallRecording;
import com.ldsilver.chingoohaja.domain.call.CallSession;
import com.ldsilver.chingoohaja.domain.call.enums.RecordingStatus;
import com.ldsilver.chingoohaja.repository.CallRecordingRepository;
import com.ldsilver.chingoohaja.repository.CallRepository;
import com.ldsilver.chingoohaja.repository.CallSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallCleanupSchedulerService {

    private final CallRepository callRepository;
    private final CallSessionRepository callSessionRepository;
    private final CallRecordingRepository callRecordingRepository;
    private final AgoraRecordingService agoraRecordingService;
    private final CallChannelService callChannelService;

    @Scheduled(fixedDelay = 300000) // 5분 = 300,000ms
    @Transactional
    public void cleanupStaleResources() {
        log.info("🧹 고아 리소스 정리 스케줄러 실행");

        try {
            LocalDateTime twoHoursAgo = LocalDateTime.now().minusHours(2);

            // 1. 2시간 이상 IN_PROGRESS인 Call 정리
            cleanupStaleCalls(twoHoursAgo);

            // 2. 2시간 이상 JOINED인 Session 정리
            cleanupOrphanedSessions(twoHoursAgo);

            // 3. 2시간 이상 PROCESSING인 Recording 정리
            cleanupStuckRecordings(twoHoursAgo);

            log.info("✅ 고아 리소스 정리 완료");

        } catch (Exception e) {
            log.error("❌ 고아 리소스 정리 중 오류", e);
        }
    }

    private void cleanupStaleCalls(LocalDateTime threshold) {
        List<Call> staleCalls = callRepository.findStaleInProgressCalls(threshold);

        if (staleCalls.isEmpty()) {
            log.debug("정리할 고아 Call 없음");
            return;
        }

        log.warn("🚨 고아 Call 발견: {}건", staleCalls.size());

        for (Call call : staleCalls) {
            try {
                cleanupStaleCall(call);
            } catch (Exception e) {
                log.error("고아 Call 정리 실패 - callId: {}", call.getId(), e);
            }
        }

        log.info("고아 Call 정리 완료: {}건", staleCalls.size());
    }

    private void cleanupOrphanedSessions(LocalDateTime threshold) {
        List<CallSession> orphanedSessions = callSessionRepository
                .findOrphanedJoinedSessions(threshold);

        if (orphanedSessions.isEmpty()) {
            log.debug("정리할 고아 Session 없음");
            return;
        }

        log.warn("🚨 고아 Session 발견: {}건", orphanedSessions.size());

        for (CallSession session : orphanedSessions) {
            try {
                cleanupOrphanedSession(session);
            } catch (Exception e) {
                log.error("고아 Session 정리 실패 - sessionId: {}", session.getId(), e);
            }
        }

        log.info("고아 Session 정리 완료: {}건", orphanedSessions.size());
    }

    private void cleanupStuckRecordings(LocalDateTime threshold) {
        List<CallRecording> stuckRecordings = callRecordingRepository
                .findStuckProcessingRecordings(threshold);

        if (stuckRecordings.isEmpty()) {
            log.debug("정리할 멈춘 Recording 없음");
            return;
        }

        log.warn("🚨 멈춘 Recording 발견: {}건", stuckRecordings.size());

        for (CallRecording recording : stuckRecordings) {
            try {
                cleanupStuckRecording(recording);
            } catch (Exception e) {
                log.error("멈춘 Recording 정리 실패 - recordingId: {}", recording.getId(), e);
            }
        }

        log.info("멈춘 Recording 정리 완료: {}건", stuckRecordings.size());
    }

    private void cleanupStaleCall(Call call) {
        log.info("고아 Call 정리 시작 - callId: {}", call.getId());

        // 1. Recording 중지 시도
        if (call.getAgoraChannelName() != null) {
            try {
                callRecordingRepository.findByCallId(call.getId()).ifPresent(recording -> {
                    if (recording.getRecordingStatus() == RecordingStatus.PROCESSING) {
                        try {
                            agoraRecordingService.stopRecording(call.getId());
                            log.info("고아 Call의 Recording 중지 완료 - callId: {}", call.getId());
                        } catch (Exception e) {
                            log.warn("고아 Call의 Recording 중지 실패 (무시) - callId: {}", call.getId(), e);
                        }
                    }
                });
            } catch (Exception e) {
                log.warn("고아 Call의 Recording 확인 실패 (무시) - callId: {}", call.getId(), e);
            }
        }

        // 2. 관련 Session들 종료
        try {
            LocalDateTime now = LocalDateTime.now();
            int updatedSessions = callSessionRepository.endAllSessionsForCall(call.getId(), now);
            log.info("고아 Call의 Session 종료 - callId: {}, sessions: {}", call.getId(), updatedSessions);
        } catch (Exception e) {
            log.error("고아 Call의 Session 종료 실패 - callId: {}", call.getId(), e);
        }

        // 3. Redis 채널 정리
        if (call.getAgoraChannelName() != null) {
            try {
                callChannelService.deleteChannel(call.getAgoraChannelName());
                log.info("고아 Call의 채널 삭제 완료 - callId: {}, channel: {}",
                        call.getId(), call.getAgoraChannelName());
            } catch (Exception e) {
                log.warn("고아 Call의 채널 삭제 실패 (무시) - callId: {}", call.getId(), e);
            }
        }

        // 4. Call 종료
        try {
            call.endCall();
            callRepository.save(call);
            log.info("고아 Call 종료 완료 - callId: {}, duration: {}초",
                    call.getId(), call.getDurationSeconds());
        } catch (Exception e) {
            log.error("고아 Call 종료 실패 - callId: {}", call.getId(), e);
        }
    }

    private void cleanupOrphanedSession(CallSession session) {
        log.info("고아 Session 정리 시작 - sessionId: {}, callId: {}, userId: {}",
                session.getId(), session.getCall().getId(), session.getUser().getId());

        try {
            session.leaveSession();
            callSessionRepository.save(session);

            Call call = session.getCall();
            if (call.getAgoraChannelName() != null) {
                try {
                    callChannelService.leaveChannel(
                            call.getAgoraChannelName(),
                            session.getUser().getId()
                    );
                } catch (Exception e) {
                    log.warn("고아 Session의 채널 퇴장 실패 (무시) - sessionId: {}", session.getId(), e);
                }
            }

            log.info("고아 Session 정리 완료 - sessionId: {}", session.getId());
        } catch (Exception e) {
            log.error("고아 Session 정리 실패 - sessionId: {}", session.getId(), e);
        }
    }

    private void cleanupStuckRecording(CallRecording recording) {
        log.info("멈춘 Recording 정리 시작 - recordingId: {}, callId: {}",
                recording.getId(), recording.getCall().getId());

        try {
            try {
                agoraRecordingService.stopRecording(recording.getCall().getId());
                log.info("멈춘 Recording 중지 성공 - recordingId: {}", recording.getId());
            } catch (Exception e) {
                log.warn("멈춘 Recording 중지 실패 - FAILED 처리 - recordingId: {}", recording.getId(), e);
                recording.fail();
                callRecordingRepository.save(recording);
            }
        } catch (Exception e) {
            log.error("멈춘 Recording 정리 실패 - recordingId: {}", recording.getId(), e);
        }
    }

}
