package com.ldsilver.chingoohaja.listener;

import com.ldsilver.chingoohaja.config.RecordingProperties;
import com.ldsilver.chingoohaja.dto.call.request.RecordingRequest;
import com.ldsilver.chingoohaja.event.CallStartedEvent;
import com.ldsilver.chingoohaja.event.RecordingCompletedEvent;
import com.ldsilver.chingoohaja.service.AgoraRecordingService;
import com.ldsilver.chingoohaja.service.RecordingPostProcessorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CallEventListener 테스트")
class CallEventListenerTest {

    @Mock private AgoraRecordingService agoraRecordingService;
    @Mock private RecordingProperties recordingProperties;
    @Mock private RecordingPostProcessorService recordingPostProcessorService;

    @InjectMocks private CallEventListener callEventListener;

    @Nested
    @DisplayName("handleCallStarted - 통화 시작 이벤트 처리")
    class HandleCallStarted {

        @Test
        @DisplayName("자동 녹음이 활성화되어 있고 채널명이 있으면 녹음을 시작한다")
        void givenAutoStartEnabled_whenCallStarted_thenStartsRecording() {
            // given
            when(recordingProperties.isAutoStart()).thenReturn(true);
            CallStartedEvent event = new CallStartedEvent(1L, "channel-abc");

            // when
            callEventListener.handleCallStarted(event);

            // then
            verify(agoraRecordingService).startRecording(any(RecordingRequest.class));
        }

        @Test
        @DisplayName("자동 녹음이 비활성화되어 있으면 녹음을 시작하지 않는다")
        void givenAutoStartDisabled_whenCallStarted_thenSkipsRecording() {
            // given
            when(recordingProperties.isAutoStart()).thenReturn(false);
            CallStartedEvent event = new CallStartedEvent(1L, "channel-abc");

            // when
            callEventListener.handleCallStarted(event);

            // then
            verify(agoraRecordingService, never()).startRecording(any());
        }

        @Test
        @DisplayName("채널명이 null이면 녹음을 시작하지 않는다")
        void givenNullChannelName_whenCallStarted_thenSkipsRecording() {
            // given
            when(recordingProperties.isAutoStart()).thenReturn(true);
            CallStartedEvent event = new CallStartedEvent(1L, null);

            // when
            callEventListener.handleCallStarted(event);

            // then
            verify(agoraRecordingService, never()).startRecording(any());
        }

        @Test
        @DisplayName("녹음 서비스에서 예외가 발생해도 통화는 계속 진행된다")
        void givenRecordingServiceThrows_whenCallStarted_thenDoesNotPropagate() {
            // given
            when(recordingProperties.isAutoStart()).thenReturn(true);
            doThrow(new RuntimeException("Agora 오류")).when(agoraRecordingService).startRecording(any());
            CallStartedEvent event = new CallStartedEvent(1L, "channel-abc");

            // when & then
            assertDoesNotThrow(() -> callEventListener.handleCallStarted(event));
        }
    }

    @Nested
    @DisplayName("handleRecordingCompleted - 녹음 완료 이벤트 처리")
    class HandleRecordingCompleted {

        @Test
        @DisplayName("녹음 완료 이벤트를 수신하면 AI 후처리를 시작한다")
        void givenRecordingCompleted_whenHandled_thenStartsPostProcessing() {
            // given
            RecordingCompletedEvent event = new RecordingCompletedEvent(1L, "/recordings/call_1.hls", 120, 1024L, "/recordings/user1.hls", "/recordings/user2.hls");

            // when
            callEventListener.handleRecordingCompleted(event);

            // then
            verify(recordingPostProcessorService).processRecordingForAI(event);
        }
    }
}
