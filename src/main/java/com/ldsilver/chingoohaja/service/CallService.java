package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.config.RecordingProperties;
import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.enums.CallStatus;
import com.ldsilver.chingoohaja.dto.call.request.RecordingRequest;
import com.ldsilver.chingoohaja.repository.CallRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallService {

    private final CallRepository callRepository;
    private final AgoraRecordingService agoraRecordingService;
    private final RecordingProperties recordingProperties;

    @Transactional
    public void startCall(Long callId) {
        log.debug("통화 시작 처리 - callId: {}", callId);

        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new CustomException(ErrorCode.CALL_NOT_FOUND));

        try {
            call.startCall();;
            callRepository.save(call);

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
}
