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
import jakarta.persistence.OptimisticLockException;
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
    private final RecordingProperties recordingProperties;
    private final AgoraService agoraService;

    private static final int MAX_RETRY_ATTEMPTS = 2; // 최초 시도 + 1회 재시도
    private static final int RETRY_DELAY_SECONDS = 3;

    @Transactional
    public RecordingResponse startRecording(RecordingRequest request) {
        log.debug("Cloud Recording 시작 - callId: {}, channel: {}",
                request.callId(), request.channelName());

//        AgoraHealthStatus agoraStatus = agoraService.checkHealth();
//        if (!agoraStatus.canUseCloudRecording()) {
//            log.error("Cloud Recording을 사용할 수 없는 상태 - {}", agoraStatus.statusMessage());
//            throw new CustomException(ErrorCode.AGORA_REQUEST_FAILED,
//                    "녹음 서비스가 현재 사용 불가능합니다: " + agoraStatus.statusMessage());
//        }

        Call call = callRepository.findById(request.callId())
                .orElseThrow(() -> new CustomException(ErrorCode.CALL_NOT_FOUND));

        if (!call.isInProgress()) {
            throw new CustomException(ErrorCode.CALL_NOT_IN_PROGRESS);
        }
        if (callRecordingRepository.findByCallId(request.callId()).isPresent()) {
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
            for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
                try {
                    Call call = callRepository.findById(callId).orElse(null);
                    if (call == null) {
                        log.error("녹음 시작 실패: Call을 찾을 수 없음 - callId: {}", callId);
                        return;
                    }

                    if (!call.isInProgress()) {
                        log.warn("녹음 시작 실패: 통화가 진행 중이 아님 - callId: {}, status: {}",
                                callId, call.getCallStatus());
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

                    CallRecording recording = CallRecording.create(call, resourceId, sid);
                    callRecordingRepository.save(recording);

                    log.debug("녹음 시작 성공 - callId: {}, attempt: {}/{}",
                            callId, attempt, MAX_RETRY_ATTEMPTS);
                    return;

                } catch (Exception e) {
                    log.error("❌ 녹음 시작 실패 - callId: {}, attempt: {}/{}",
                            callId, attempt, MAX_RETRY_ATTEMPTS, e);

                    if (attempt < MAX_RETRY_ATTEMPTS) {
                        log.info("⏳ {}초 후 재시도 - callId: {}", RETRY_DELAY_SECONDS, callId);
                        try {
                            Thread.sleep(RETRY_DELAY_SECONDS * 1000L);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            log.warn("재시도 대기 중 인터럽트 - callId: {}", callId);
                            return;
                        }
                    } else {
                        log.error("❌ 녹음 시작 최종 실패 - callId: {}", callId);
                        updateCallRecordingFailureStatus(callId);
                    }
                }
            }
        });
    }

    @Transactional
    public RecordingResponse stopRecording(Long callId) {
        log.debug("Cloud Recording 중지 - callId: {}", callId);

        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new CustomException(ErrorCode.CALL_NOT_FOUND));

        CallRecording recording = callRecordingRepository.findByCallIdWithCall(callId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECORDING_NOT_STARTED));

        if (recording.getRecordingStatus() != RecordingStatus.PROCESSING) {
            log.warn("이미 종료된 녹음 - callId: {}, status: {}", callId, recording.getRecordingStatus());
            return RecordingResponse.from(recording, call);
        }

        String resourceId = recording.getAgoraResourceId();
        String sid = recording.getAgoraSid();
        String channelName = call.getAgoraChannelName();

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                Map<String, Object> stopResponse = cloudRecordingClient.stopRecording(
                        resourceId, sid, channelName
                ).block();

                // ✅ 404 에러일 때 Query API로 파일 정보 조회
                if (stopResponse != null && stopResponse.containsKey("code")
                        && Integer.valueOf(404).equals(stopResponse.get("code"))) {
                    log.warn("⚠️ Stop 실패 (404) - Query API로 파일 정보 조회 시도. callId: {}",
                            callId);
                    return handleRecordingAlreadyStopped(recording, call, resourceId, sid);

                }


                if (stopResponse == null || stopResponse.isEmpty()) {
                    log.warn("녹음 중지 응답이 비어있음 - callId: {}", callId);
                    recording.complete(null, null, "hls");
                    callRecordingRepository.saveAndFlush(recording);
                    return RecordingResponse.from(recording, call);
                }

                // 정상 응답 처리
                log.debug("🔍 Stop Response: {}", stopResponse);

                String fileUrl = extractFileUrl(stopResponse);
                Long fileSize = extractFileSize(stopResponse);
                String finalFileUrl = downloadAndStoreRecordingFile(fileUrl, callId);

                recording.complete(finalFileUrl, fileSize, "hls");
                callRecordingRepository.saveAndFlush(recording);

                log.info("✅ Recording 중지 성공 - callId: {}, attempt: {}/{}",
                        callId, attempt, MAX_RETRY_ATTEMPTS);

                return RecordingResponse.stopped(
                        resourceId, sid, callId, channelName, finalFileUrl, fileSize,
                        recording.getRecordingStartedAt(), recording.getRecordingDurationSeconds()
                );

            } catch (CustomException e) {
                if (e.getErrorCode() == ErrorCode.RECORDING_RESOURCE_NOT_FOUND) {
                    log.warn("녹음 리소스 없음 - callId: {}", callId);
                    recording.complete(null, null, "hls");
                    callRecordingRepository.saveAndFlush(recording);
                    return RecordingResponse.from(recording, call);
                }

                log.error("❌ Recording 중지 실패 - callId: {}, attempt: {}/{}",
                        callId, attempt, MAX_RETRY_ATTEMPTS, e);

                if (attempt >= MAX_RETRY_ATTEMPTS) {
                    handleRecordingFailure(recording, callId);
                    throw e;
                }

                // 재시도 대기
                sleepForRetry(callId);


            } catch (Exception e) {
                log.error("❌ Cloud Recording 중지 실패 - callId: {}", callId, e);
                if (attempt >= MAX_RETRY_ATTEMPTS) {
                    handleRecordingFailure(recording, callId);
                    throw new CustomException(ErrorCode.RECORDING_STOP_FAILED);
                }

                sleepForRetry(callId);
            }
        }

        // 이 지점에 도달하면 모든 재시도 실패
        handleRecordingFailure(recording, callId);
        throw new CustomException(ErrorCode.RECORDING_STOP_FAILED, "모든 재시도 실패");
    }



    @Transactional(readOnly = true)
    public RecordingResponse getRecordingStatus(Long callId) {
        log.debug("Recording 상태 조회 - callId: {}", callId);

        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new CustomException(ErrorCode.CALL_NOT_FOUND));

        CallRecording recording = callRecordingRepository.findByCallId(callId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECORDING_NOT_STARTED));

        try {
            Map<String, Object> queryResponse = cloudRecordingClient.queryRecording(
                    recording.getAgoraResourceId(), recording.getAgoraSid()
            ).block();

            if (queryResponse == null) {
                return RecordingResponse.failed(
                        recording.getAgoraResourceId(),
                        recording.getAgoraSid(),
                        callId,
                        call.getAgoraChannelName());
            }

            RecordingStatus status = extractRecordingStatus(queryResponse);

            return new RecordingResponse(
                    recording.getAgoraResourceId(),
                    recording.getAgoraSid(),
                    callId,
                    call.getAgoraChannelName(),
                    status,
                    recording.getFilePath(),
                    recording.getFileSize(),
                    recording.getRecordingStartedAt(),
                    recording.getRecordingEndedAt(),
                    recording.getRecordingDurationSeconds()
            );
        } catch (Exception e) {
            log.error("Recording 상태 조회 실패 - callId: {}", callId, e);
            return RecordingResponse.failed(recording.getAgoraResourceId(), recording.getAgoraSid(),
                    callId, call.getAgoraChannelName());
        }
    }

    @Async("recordingTaskExecutor")
    @Transactional
    public CompletableFuture<Void> autoStopRecordingOnCallEnd(Long callId) {
        log.debug("통화 종료로 인한 자동 Recording 중지 - callId: {}", callId);

        return CompletableFuture.runAsync(() -> {
            try {
                if (callRecordingRepository.findByCallId(callId).isPresent()) {
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

        List<CallRecording> recordings = callRecordingRepository.findByRecordingStatus(RecordingStatus.PROCESSING);

        return recordings.stream()
                .map(recording -> new RecordingResponse(
                        recording.getAgoraResourceId(),
                        recording.getAgoraSid(),
                        recording.getCall().getId(),
                        recording.getCall().getAgoraChannelName(),
                        RecordingStatus.PROCESSING,
                        recording.getFilePath(),
                        recording.getFileSize(),
                        recording.getRecordingStartedAt(),
                        recording.getRecordingEndedAt(),
                        recording.getRecordingDurationSeconds()
                ))
                .toList();
    }



    /**
     * Recording이 이미 종료된 경우 처리 (404)
     */
    private RecordingResponse handleRecordingAlreadyStopped(
            CallRecording recording, Call call, String resourceId, String sid) {
        try {
            // Query API로 파일 정보 조회 시도
            Map<String, Object> queryResponse = cloudRecordingClient
                    .queryRecording(resourceId, sid)
                    .block();

            if (queryResponse != null) {
                String fileUrl = extractFileUrl(queryResponse);
                Long fileSize = extractFileSize(queryResponse);

                if (fileUrl != null && !fileUrl.isEmpty()) {
                    String finalFileUrl = downloadAndStoreRecordingFile(fileUrl, call.getId());
                    recording.complete(finalFileUrl, fileSize, "hls");
                    callRecordingRepository.saveAndFlush(recording);
                    log.info("✅ Query API로 파일 정보 획득 - callId: {}", call.getId());
                    return RecordingResponse.from(recording, call);
                }
            }
        } catch (Exception queryEx) {
            log.warn("Query API 실패 - callId: {}", call.getId(), queryEx);
        }

        // Query 실패 시 파일 없이 완료 처리
        recording.complete(null, null, "hls");
        callRecordingRepository.saveAndFlush(recording);
        log.warn("⚠️ 파일 정보 없이 완료 처리 - callId: {}", call.getId());
        return RecordingResponse.from(recording, call);
    }


    private void handleRecordingFailure(CallRecording recording, Long callId) {
        try {
            recording.fail();
            callRecordingRepository.saveAndFlush(recording);  // ✅ flush 추가
        } catch (OptimisticLockException lockEx) {
            log.warn("⚠️ Recording 실패 상태 저장 시 낙관적 락 실패 (무시) - callId: {}", callId);
            // ✅ 이미 다른 트랜잭션에서 처리됨 - 무시
        } catch (Exception saveEx) {
            log.error("Recording 실패 상태 저장 실패 - callId: {}", callId, saveEx);
        }
    }

    /**
     * 재시도 대기
     */
    private void sleepForRetry(Long callId) {
        try {
            log.info("⏳ {}초 후 재시도 - callId: {}", RETRY_DELAY_SECONDS, callId);
            Thread.sleep(RETRY_DELAY_SECONDS * 1000L);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("재시도 대기 중 인터럽트 - callId: {}", callId);
        }
    }

    private void updateCallRecordingFailureStatus(Long callId) {
        try {
            callRecordingRepository.findByCallId(callId).ifPresent(recording -> {
                recording.fail();
                callRecordingRepository.save(recording);
            });
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

    private String extractFileUrl(Map<String, Object> response) {
        log.debug("=" .repeat(80));
        log.debug("🔍 파일 URL 추출 시작");
        log.debug("=" .repeat(80));
        log.debug("전체 응답: {}", response);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> serverResponse = (Map<String, Object>) response.get("serverResponse");

            if (serverResponse == null) {
                log.warn("⚠️ serverResponse가 null");
                log.debug("=" .repeat(80));
                return null;
            }

            log.debug("📦 serverResponse: {}", serverResponse);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> fileList = (List<Map<String, Object>>) serverResponse.get("fileList");

            if (fileList == null) {
                log.warn("⚠️ fileList가 null");
                log.debug("=" .repeat(80));
                return null;
            }

            if (fileList.isEmpty()) {
                log.warn("⚠️ fileList가 비어있음");
                log.debug("=" .repeat(80));
                return null;
            }

            log.debug("✅ fileList 발견! 개수: {}", fileList.size());

            for (int i = 0; i < fileList.size(); i++) {
                Map<String, Object> file = fileList.get(i);
                log.debug("  📁 파일 [{}]: {}", i, file);
            }

            Map<String, Object> firstFile = fileList.get(0);
            String fileName = (String) firstFile.get("fileName");

            log.debug("=" .repeat(80));
            log.debug("✅ 추출된 fileName: {}", fileName);
            log.debug("=" .repeat(80));

            return fileName;

        } catch (Exception e) {
            log.error("=" .repeat(80));
            log.error("❌ 파일 URL 추출 실패", e);
            log.error("=" .repeat(80));
        }

        return null;
    }

    private Long extractFileSize(Map<String, Object> response) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> serverResponse = (Map<String, Object>) response.get("serverResponse");

            if (serverResponse != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> fileList = (List<Map<String, Object>>) serverResponse.get("fileList");

                if (fileList != null && !fileList.isEmpty()) {
                    Map<String, Object> firstFile = fileList.get(0);
                    Object fileSize = firstFile.get("fileSize");

                    if (fileSize instanceof Number) {
                        long size = ((Number) fileSize).longValue();
                        log.debug("📊 파일 크기: {} bytes", size);
                        return size;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("파일 크기 추출 실패", e);
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
