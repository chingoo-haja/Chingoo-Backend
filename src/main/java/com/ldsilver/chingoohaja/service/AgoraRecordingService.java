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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
        if (call.isRecordingActive()) {
            throw new CustomException(ErrorCode.RECORDING_ALREADY_STARTED);
        }

        startRecordingAsync(request.callId(), request.channelName());

        return RecordingResponse.started("pending", "pending", request.callId(), request.channelName());
    }

    @Async("recordingTaskExecutor")
    @Transactional
    public CompletableFuture<Void> startRecordingAsync(Long callId, String channelName) {
        log.debug("비동기 녹음 시작 - callId: {}", callId);

        return CompletableFuture.runAsync(() -> {
            try {
                Call call = callRepository.findById(callId).orElse(null);
                if (call == null || !call.isInProgress()) {
                    log.warn("녹음 시작 실패: 통화가 없거나 진행 중이 아님 - callId: {}", callId);
                    return;
                }

                String resourceId = cloudRecordingClient.acquireResource(channelName).block();
                if (resourceId == null) {
                    throw new CustomException(ErrorCode.CALL_SESSION_ERROR, "Resource 획득 실패");
                }

                RecordingRequest request = RecordingRequest.of(callId, channelName);
                String sid = cloudRecordingClient.startRecording(
                        resourceId, channelName, request
                ).block();

                if (sid == null) {
                    throw new CustomException(ErrorCode.CALL_SESSION_ERROR, "Recording 시작 실패");
                }

                call.startCloudRecording(resourceId, sid);
                callRepository.save(call);

                log.debug("비동기 녹음 시작 완료 - callId: {}, resourceId: {}, sid: {}", callId, maskId(resourceId), maskId(sid));
            } catch (Exception e) {
                log.error("비동기 녹음 시작 실패 - callId: {}", callId, e);
                updateCallRecordingFailureStatus(callId);
            }
        });
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
            Long fileSize = extractFileSize(stopResponse);

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

    @Async("recordingTaskExecutor")
    @Transactional
    public CompletableFuture<Void> autoStopRecordingOnCallEnd(Long callId) {
        log.debug("통화 종료로 인한 자동 Recording 중지 - callId: {}", callId);

        return CompletableFuture.runAsync(() -> {
            try {
                Call call = callRepository.findById(callId).orElse(null);
                if (call != null && call.isRecordingActive()) {
                    stopRecording(callId);
                    log.info("통화 종료로 인한 자동 Recording 중지 완료 - callId: {}", callId);
                }
            } catch (Exception e) {
                log.error("자동 Recording 중지 실패 - callId: {}", callId, e);
            }
        });
    }

    @Transactional(readOnly = true)
    public List<RecordingResponse> getActiveRecordings() {
        log.debug("활성 Recording 목록 조회");

        List<Call> recordingCalls = callRepository.findRecordingCalls();

        return recordingCalls.stream()
                .map(call -> new RecordingResponse(
                        call.getAgoraResourceId(),
                        call.getAgoraSid(),
                        call.getId(),
                        call.getAgoraChannelName(),
                        RecordingStatus.PROCESSING,
                        call.getRecordingFileUrl(),
                        null,
                        call.getRecordingStartedAt(),
                        call.getRecordingEndedAt(),
                        call.getRecordingDurationSeconds()
                ))
                .toList();
    }



    private void updateCallRecordingFailureStatus(Long callId) {
        try {
            Call call = callRepository.findById(callId).orElse(null);
            if (call != null) {
                call.stopCloudRecording(null);
                callRepository.save(call);
            }
        } catch (Exception e) {
            log.error("Recording 실패 상태 업데이트 실패 - callId: {}", callId, e);
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

    private Long extractFileSize(Map<String, Object> stopResponse) {
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
                    case "0", "1", "2", "3", "4", "5" -> RecordingStatus.PROCESSING;
                    case "6", "7", "8" -> RecordingStatus.COMPLETED;
                    case "20" -> RecordingStatus.FAILED;
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
