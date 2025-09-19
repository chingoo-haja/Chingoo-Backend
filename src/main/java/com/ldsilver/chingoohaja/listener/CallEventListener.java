package com.ldsilver.chingoohaja.listener;

import com.ldsilver.chingoohaja.config.RecordingProperties;
import com.ldsilver.chingoohaja.dto.call.request.RecordingRequest;
import com.ldsilver.chingoohaja.event.CallStartedEvent;
import com.ldsilver.chingoohaja.service.AgoraRecordingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class CallEventListener {

    private final AgoraRecordingService agoraRecordingService;
    private final RecordingProperties recordingProperties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCallStarted(CallStartedEvent event) {
        if (!recordingProperties.isAutoStart() || event.getChannelName() == null) {
            log.debug("자동 녹음 비활성화 또는 채널명 없음 - callId: {}", event.getCallId());
            return;
        }

        try {
            log.debug("트랜잭션 커밋 후 자동 녹음 시작 - callId: {}", event.getCallId());
            RecordingRequest request = RecordingRequest.of(event.getCallId(), event.getChannelName());
            agoraRecordingService.startRecording(request);
            log.info("자동 녹음 시작 완료 - callId: {}", event.getCallId());
        } catch (Exception e) {
            log.error("자동 녹음 시작 실패 - callId: {} (통화는 정상 진행)", event.getCallId(), e);
        }
    }
}
