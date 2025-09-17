package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.config.RecordingProperties;
import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.dto.call.request.RecordingRequest;
import com.ldsilver.chingoohaja.dto.call.response.RecordingResponse;
import com.ldsilver.chingoohaja.infrastructure.agora.AgoraCloudRecordingClient;
import com.ldsilver.chingoohaja.repository.CallRecordingRepository;
import com.ldsilver.chingoohaja.repository.CallRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgoraRecordingService {

    private final AgoraCloudRecordingClient cloudRecordingClient;
    private final CallRepository callRepository;
    private final CallRecordingRepository callRecordingRepository;
    private final FirebaseStorageService firebaseStorageService;
    private final RecordingProperties recordingProperties;

    @Transactional
    public RecordingResponse startRecording(RecordingRequest request) {
        log.debug("Cloud Recording 시작 - callId: {}, channel: {}",
                request.callId(), request.channelName());

        Call call = callRepository.findById(request.callId())
                .orElseThrow(() -> new CustomException(ErrorCode.CALL_NOT_FOUND));

        if (!call.isInProgress()) {
            throw new CustomException(ErrorCode.CALL_NOT_IN_PROGRESS);
        }
        if (!call.isRecordingActive()) {
            throw new CustomException(ErrorCode.RECORDING_ALREADY_STARTED);
        }

        try {
            String resourceId = cloudRecordingClient.acpireResource(request.channelName()).block();
            if (resourceId == null) {
                throw new CustomException(ErrorCode.INVALID_RESOURCE_ID);
            }

            String sid = cloudRecordingClient.startRecording(
                    resourceId, request.channelName(), request, recordingProperties.getFileFormats()
            ).block();
            if (sid == null) {
                throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
            }

            call.startCloudRecording(resourceId, sid);
            callRepository.save(call);

            log.info("Cloud Recording 시작 성공 - callId: {}, resourceId: {}, sid: {}",
                    request.callId(), maskId(resourceId), maskId(sid));

            return RecordingResponse.started(resourceId, sid, request.callId(), request.channelName());
        } catch (Exception e) {
            log.error("Cloud Recording 시작 실패 - callId: {}", request.callId());
            if (e instanceof  CustomException) {throw e;}
            throw new CustomException(ErrorCode.RECORDING_START_FAILED);
        }
    }

    private String maskId(String id) {
        if (id == null || id.length() < 8) {
            return "***";
        }
        return id.substring(0,4) + "***" + id.substring(id.length() - 4);
    }
}
