package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.config.RecordingProperties;
import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.CallRecording;
import com.ldsilver.chingoohaja.event.RecordingCompletedEvent;
import com.ldsilver.chingoohaja.repository.CallRecordingRepository;
import com.ldsilver.chingoohaja.repository.CallRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingPostProcessorService {

    private final CallRepository callRepository;
    private final CallRecordingRepository callRecordingRepository;
    private final FirebaseStorageService firebaseStorageService;
    private final AudioConverterService audioConverterService;
    private final RecordingProperties recordingProperties;

    /**
     * Recording 후처리 메인 로직
     * - 5분 이상 통화만 WAV 변환
     * - 각 사용자별로 분리된 WAV 생성
     */
    @Async("recordingTaskExecutor")
    @Transactional
    public void processRecordingForAI(RecordingCompletedEvent event) {
        Long callId = event.getCallId();

        log.debug("=" .repeat(80));
        log.debug("🔄 Recording 후처리 시작 - callId: {}", callId);
        log.debug("=" .repeat(80));

        try {
            // 1. 설정 확인
            if (!recordingProperties.getAiTraining().isAutoConvert()) {
                log.debug("자동 변환 비활성화 - callId: {}", callId);
                return;
            }

            // 2. 통화 시간 확인 (5분 미만은 스킵)
            Integer durationSeconds = event.getDurationSeconds();
            int minDuration = recordingProperties.getAiTraining().getMinDurationSeconds();

            if (durationSeconds == null || durationSeconds < minDuration) {
                log.debug("통화 시간 부족으로 변환 스킵 - callId: {}, duration: {}초 (최소: {}초)",
                        callId, durationSeconds, minDuration);
                return;
            }

            log.info("✅ 변환 조건 충족 - callId: {}, duration: {}초", callId, durationSeconds);

            // 3. Call 정보 조회
            Call call = callRepository.findById(callId).orElse(null);
            if (call == null) {
                log.error("❌ Call 조회 실패 - callId: {}", callId);
                return;
            }

            CallRecording recording = callRecordingRepository.findByCallId(callId).orElse(null);
            if (recording == null) {
                log.error("❌ CallRecording 조회 실패 - callId: {}", callId);
                return;
            }

            // 4. HLS 파일 다운로드
            String hlsPath = event.getFilePath();
            if (hlsPath == null || hlsPath.trim().isEmpty()) {
                log.error("❌ HLS 파일 경로 없음 - callId: {}", callId);
                return;
            }

            log.debug("📥 HLS 파일 다운로드 - path: {}", hlsPath);
            byte[] hlsData = downloadHlsFile(hlsPath);

            // 5. 사용자별 WAV 변환 및 업로드
            Long user1Id = call.getUser1().getId();
            Long user2Id = call.getUser2().getId();

            String user1WavPath = convertAndUploadWav(hlsData, callId, user1Id, "user1");
            String user2WavPath = convertAndUploadWav(hlsData, callId, user2Id, "user2");

            log.info("✅ WAV 변환 완료 - callId: {}, user1: {}, user2: {}",
                    callId, user1WavPath, user2WavPath);

            // 6. (옵션) HLS 원본 삭제
            if (!recordingProperties.getAiTraining().isKeepOriginalHls()) {
                deleteHlsFile(hlsPath, callId);
            }


        } catch (Exception e) {
            log.error("❌ Recording 후처리 실패 - callId: {}", callId, e);
        }
    }


    private byte[] downloadHlsFile(String filePath) {
        // GCS 경로 (gs://bucket/path) 또는 HTTP URL 구분
        if (filePath.startsWith("gs://")) {
            String path = filePath.substring(filePath.indexOf("/", 5) + 1);
            return firebaseStorageService.downloadFile(path);
        } else if (filePath.startsWith("http")) {
            return firebaseStorageService.downloadFromUrl(filePath);
        } else {
            // 상대 경로
            return firebaseStorageService.downloadFile(filePath);
        }
    }


    private String convertAndUploadWav(byte[] hlsData, Long callId, Long userId, String userLabel) {
        try {
            log.info("🔄 {} WAV 변환 시작 - callId: {}, userId: {}", userLabel, callId, userId);

            // 1. HLS → WAV 변환
            String outputFileName = String.format("call_%d_%s", callId, userLabel);
            byte[] wavData = audioConverterService.convertHlsToWav(hlsData, outputFileName);

            // 2. GCS 업로드 경로 생성
            String wavPath = generateWavPath(callId, userId, userLabel);

            // 3. GCS 업로드
            String uploadedPath = firebaseStorageService.uploadRecordingFile(
                    wavData,
                    wavPath,
                    "audio/wav"
            );

            log.info("✅ {} WAV 업로드 완료 - callId: {}, path: {}",
                    userLabel, callId, uploadedPath);

            return uploadedPath;

        } catch (Exception e) {
            log.error("❌ {} WAV 변환 실패 - callId: {}, userId: {}",
                    userLabel, callId, userId, e);
            throw e;
        }
    }


    private String generateWavPath(Long callId, Long userId, String userLabel) {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return String.format("recordings/ai-training/%s/call_%d_%s_%d.wav",
                date, callId, userLabel, userId);
    }


    private void deleteHlsFile(String filePath, Long callId) {
        try {
            log.info("🗑️ HLS 원본 삭제 - callId: {}, path: {}", callId, filePath);

            if (filePath.startsWith("gs://")) {
                String path = filePath.substring(filePath.indexOf("/", 5) + 1);
                firebaseStorageService.deleteFile(path);
            } else if (!filePath.startsWith("http")) {
                firebaseStorageService.deleteFile(filePath);
            } else {
                log.warn("⚠️ HTTP URL은 삭제 불가 - callId: {}", callId);
            }

        } catch (Exception e) {
            log.warn("⚠️ HLS 원본 삭제 실패 (무시) - callId: {}", callId, e);
        }
    }
}