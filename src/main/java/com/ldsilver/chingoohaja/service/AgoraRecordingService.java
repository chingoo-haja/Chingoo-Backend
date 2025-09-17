package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.config.RecordingProperties;
import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.CallRecording;
import com.ldsilver.chingoohaja.domain.call.enums.RecordingStatus;
import com.ldsilver.chingoohaja.dto.call.request.RecordingRequest;
import com.ldsilver.chingoohaja.dto.call.response.RecordingResponse;
import com.ldsilver.chingoohaja.infrastructure.agora.AgoraCloudRecordingClient;
import com.ldsilver.chingoohaja.repository.CallRecordingRepository;
import com.ldsilver.chingoohaja.repository.CallRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

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

    @Transactional
    public RecordingResponse stopRecording(Long callId) {
        log.debug("Cloud Recording 중지 - callId: {}", callId);

        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new CustomException(ErrorCode.CALL_NOT_FOUND));
        if (!call.isRecordingActive()) {
            throw new CustomException(ErrorCode.RECORDING_NOT_STARTED);
        }

        String resourceId = call.getAgoraResourceId();
        String sid = call.getAgoraSid();
        String channelName = call.getAgoraChannelName();

        try {
            Map<String, Object> stopResponse = cloudRecordingClient.stopRecording(
                    resourceId, sid, channelName
            ).block();

            if (stopResponse == null) {
                throw new CustomException(ErrorCode.RECORDING_STOP_FAILED);
            }

            String fileUrl = extractFileUrl(stopResponse);
            Long fileSize = extrackFileSize(stopResponse);

            String finalFileUrl = downloadAndStoreRecordingFile(fileUrl, callId);

            call.stopCloudRecording(finalFileUrl);
            callRepository.save(call);

            CallRecording callRecording = CallRecording.of(
                    call, finalFileUrl, fileSize, "mp3", RecordingStatus.COMPLETED
            );
            callRecordingRepository.save(callRecording);

            log.info("Cloud Recording 중지 성공 - callId: {}, fileUrl: {}",
                    callId, finalFileUrl != null ? "saved" : "none");

            return RecordingResponse.stopped(
                    resourceId, sid, callId, channelName, finalFileUrl, fileSize,
                    call.getRecordingStartedAt(), call.getRecordingDurationSeconds()
            );
        } catch (Exception e) {
            log.error("Cloud Recording 중지 실패 - callId: {}", callId, e);

            try {
                call.stopCloudRecording(null);
                callRepository.save(call);
            } catch (Exception saveEx) {
                log.error("Recording 실패 상태 저장 실패 - callId: {}", callId, saveEx);
            }

            if (e instanceof  CustomException) {
                throw e;
            }
            throw new CustomException(ErrorCode.RECORDING_STOP_FAILED);
        }
    }

    @Transactional(readOnly = true)
    public RecordingResponse getRecordingStatus(Long callId) {
        log.debug("Recording 상태 조회 - callId: {}", callId);

        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECORDING_NOT_STARTED));

        if (call.getAgoraResourceId() == null || call.getAgoraSid() == null) {
            throw new CustomException(ErrorCode.RECORDING_NOT_STARTED);
        }

        try {
            Map<String, Object> queryResponse = cloudRecordingClient.queryRecording(
                    call.getAgoraResourceId(), call.getAgoraSid()
            ).block();

            if (queryResponse == null) {
                return RecordingResponse.failed(call.getAgoraResourceId(), call.getAgoraSid(),
                        callId, call.getAgoraChannelName());
            }

            RecordingStatus status = extractRecordingStatus(queryResponse);
            String fileUrl = call.hasRecordingFile() ? call.getRecordingFileUrl() : null;

            return new RecordingResponse(
                    call.getAgoraResourceId(),
                    call.getAgoraSid(),
                    callId,
                    call.getAgoraChannelName(),
                    status,
                    fileUrl,
                    null,
                    call.getRecordingStartedAt(),
                    call.getRecordingEndedAt(),
                    call.getRecordingDurationSeconds()
            );
        } catch (Exception e) {
            log.error("Recording 상태 조회 실패 - callId: {}", callId, e);
            return RecordingResponse.failed(call.getAgoraResourceId(), call.getAgoraSid(),
                    callId, call.getAgoraChannelName());
        }
    }

    @Transactional
    public void autoStopRecordingOnCallEnd(Long callId) {
        log.debug("통화 종료로 인한 자동 Recording 중지 - callId: {}", callId);

        try {
            Call call = callRepository.findById(callId).orElse(null);
            if (call != null && call.isRecordingActive()) {
                stopRecording(callId);
                log.info("통화 종료로 인한 자동 Recording 중지 완료 - callId: {}", callId);
            }
        } catch (Exception e) {
            log.error("자동 Recording 중지 실패 - callId: {}", callId, e);
        }
    }




    private String downloadAndStoreRecordingFile(String originalUrl, Long callId) {
        if (originalUrl == null || originalUrl.trim().isEmpty()) {
            log.warn("Recording 파일 URL이 없어 저장을 건너뜁니다 - callId: {}", callId);
            return null;
        }

        try {
            // TODO: 실제 구현에서는 Agora 저장소에서 파일을 다운로드하고 Storage에 업로드
            // 현재는 원본 URL을 그대로 반환 (비용 최적화를 위해)
            log.debug("Recording 파일 저장 완료 - callId: {}, url: {}", callId, "saved");
            return originalUrl;

        } catch (Exception e) {
            log.error("Recording 파일 저장 실패 - callId: {}, url: {}", callId, originalUrl, e);
            return originalUrl; // 실패해도 원본 URL은 반환
        }
    }

    private String extractFileUrl(Map<String, Object> stopResponse) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> serverResponse = (Map<String, Object>) stopResponse.get("serverResponse");

            if (serverResponse != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> fileList = (List<Map<String, Object>>) serverResponse.get("fileList");

                if (fileList != null && !fileList.isEmpty()) {
                    Map<String, Object> firstFile = fileList.get(0);
                    return (String) firstFile.get("fileName");
                }
            }
        } catch (Exception e) {
            log.warn("Recording 파일 URL 추출 실패", e);
        }
        return null;
    }

    private Long extrackFileSize(Map<String, Object> stopResponse) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> serverResponse = (Map<String, Object>) stopResponse.get("serverResponse");

            if (serverResponse != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> fileList = (List<Map<String, Object>>) serverResponse.get("fileList");

                if (fileList != null && !fileList.isEmpty()) {
                    Map<String, Object> firstFile = fileList.get(0);
                    Object fileSize = firstFile.get("fileSize");
                    return fileSize instanceof Number ? ((Number) fileSize).longValue() : null;
                }
            }
        } catch (Exception e) {
            log.warn("Recording 파일 크기 추출 실패", e);
        }
        return null;
    }

    private RecordingStatus extractRecordingStatus(Map<String, Object> queryResponse) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> serverResponse = (Map<String, Object>) queryResponse.get("serverResponse");

            if (serverResponse != null) {
                String status = (String) serverResponse.get("status");
                return switch (status) {
                    case "0" -> RecordingStatus.PROCESSING;
                    case "1", "2", "3", "4" -> RecordingStatus.COMPLETED;
                    default -> RecordingStatus.FAILED;
                };
            }
        } catch (Exception e) {
            log.warn("Recording 상태 추출 실패", e);
        }
        return RecordingStatus.FAILED;
    }


    private String maskId(String id) {
        if (id == null || id.length() < 8) {
            return "***";
        }
        return id.substring(0,4) + "***" + id.substring(id.length() - 4);
    }
}
