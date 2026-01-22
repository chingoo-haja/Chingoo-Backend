package com.ldsilver.chingoohaja.listener;

import com.ldsilver.chingoohaja.config.RecordingProperties;
import com.ldsilver.chingoohaja.dto.call.request.RecordingRequest;
import com.ldsilver.chingoohaja.event.CallStartedEvent;
import com.ldsilver.chingoohaja.event.RecordingCompletedEvent;
import com.ldsilver.chingoohaja.service.AgoraRecordingService;
import com.ldsilver.chingoohaja.service.RecordingPostProcessorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CallEventListener {

    private final AgoraRecordingService agoraRecordingService;
    private final RecordingProperties recordingProperties;
    private final RecordingPostProcessorService recordingPostProcessorService;

    @Async("recordingTaskExecutor")
    @EventListener
    public void handleCallStarted(CallStartedEvent event) {
        if (!recordingProperties.isAutoStart() || event.getChannelName() == null) {
            log.debug("자동 녹음 비활성화 또는 채널명 없음 - callId: {}", event.getCallId());
            return;
        }

        try {
            log.debug("자동 녹음 시작 이벤트 수신 - callId: {}", event.getCallId());

            RecordingRequest request = RecordingRequest.of(event.getCallId(), event.getChannelName());
            agoraRecordingService.startRecording(request);

            log.info("자동 녹음 시작 완료 - callId: {}", event.getCallId());
        } catch (Exception e) {
            log.error("자동 녹음 시작 실패 - callId: {} (통화는 정상 진행)", event.getCallId(), e);
        }
    }

    @Async("recordingTaskExecutor")
    @EventListener
    public void handleRecordingCompleted(RecordingCompletedEvent event) {
        log.info("RecordingCompletedEvent 수신 - callId: {}, duration: {}초",
                event.getCallId(), event.getDurationSeconds());

        recordingPostProcessorService.processRecordingForAI(event);
    }
}
