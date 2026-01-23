package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.config.RecordingProperties;
import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.CallRecording;
import com.ldsilver.chingoohaja.dto.call.RecordingInfo;
import com.ldsilver.chingoohaja.event.RecordingCompletedEvent;
import com.ldsilver.chingoohaja.repository.CallRecordingRepository;
import com.ldsilver.chingoohaja.repository.CallRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    public void processRecordingForAI(RecordingCompletedEvent event) {
        Long callId = event.getCallId();

        log.debug("=" .repeat(80));
        log.debug("🔄 Recording 후처리 시작 - callId: {}", callId);
        log.debug("=" .repeat(80));

        Path user1TempDir = null;
        Path user2TempDir = null;

        Path tempDir = null;

        try {
            RecordingProperties.AiTrainingConfig aiConfig = recordingProperties.getAiTraining();

            // 1. 설정 확인
            if (aiConfig == null) {
                log.warn("⚠️ AI Training 설정이 없습니다 - callId: {}", callId);
                return;
            }

            if (!aiConfig.isAutoConvert()) {
                log.info("⏭️ 자동 변환 비활성화 - callId: {}", callId);
                return;
            }

            // 2. 통화 시간 확인 (5분 미만은 스킵)
            Integer durationSeconds = event.getDurationSeconds();
            int minDuration = aiConfig.getMinDurationSeconds();

            if (durationSeconds == null || durationSeconds < minDuration) {
                log.debug("통화 시간 부족으로 변환 스킵 - callId: {}, duration: {}초 (최소: {}초)",
                        callId, durationSeconds, minDuration);
                return;
            }

            // 3. DB 조회
            RecordingInfo recordingInfo = getRecordingInfo(callId);
            if (recordingInfo == null) {
                log.error("❌ Recording 정보 조회 실패 - callId: {}", callId);
                return;
            }

            String user1HlsPath = event.getUser1FilePath();
            String user2HlsPath = event.getUser2FilePath();

            if (user1HlsPath == null || user2HlsPath == null) {
                log.error("❌ 사용자별 HLS 경로 없음 - callId: {}", callId);
                return;
            }

            // 4. HLS 파일 다운로드
//            String hlsPath = recordingInfo.hlsPath();
//            if (hlsPath == null || hlsPath.trim().isEmpty()) {
//                log.error("❌ HLS 파일 경로 없음 - callId: {}", callId);
//                return;
//            }

            log.info("✅ 변환 조건 충족 - callId: {}, duration: {}초", callId, durationSeconds);

//            tempDir = Files.createTempDirectory("hls-convert-");
//            Path localM3u8 = firebaseStorageService.downloadHlsDirectory(hlsPath, tempDir);
//            log.debug("📥 HLS 디렉토리 다운로드 완료 - callId: {}", callId);

            // 5. 사용자별 WAV 변환
            user1TempDir = Files.createTempDirectory("hls-user1-");
            Path user1M3u8 = firebaseStorageService.downloadHlsDirectory(user1HlsPath, user1TempDir);
            String user1WavPath = convertAndUploadWavFromLocal(
                    user1M3u8, callId, recordingInfo.user1Id(), "user1");

            user2TempDir = Files.createTempDirectory("hls-user2-");
            Path user2M3u8 = firebaseStorageService.downloadHlsDirectory(user2HlsPath, user2TempDir);
            String user2WavPath = convertAndUploadWavFromLocal(
                    user2M3u8, callId, recordingInfo.user2Id(), "user2");


            log.info("✅ WAV 변환 완료 - callId: {}, user1: {}, user2: {}",
                    callId, user1WavPath, user2WavPath);

            // 6. HLS 원본 삭제
            if (!aiConfig.isKeepOriginalHls()) {
                deleteHlsFile(user1HlsPath, callId);
                deleteHlsFile(user2HlsPath, callId);
            }

            log.debug("✅ Recording 후처리 완료 - callId: {}", callId);

        } catch (Exception e) {
            log.error("=" .repeat(80));
            log.error("❌ Recording 후처리 실패 - callId: {}", callId, e);
            log.error("=" .repeat(80));
        } finally {
            if (user1TempDir != null) {
                cleanupTempDirectory(user1TempDir);
            }
            if (user2TempDir != null) {
                cleanupTempDirectory(user2TempDir);
            }
        }
    }

    @Transactional(readOnly = true)
    public RecordingInfo getRecordingInfo(Long callId) {
        Call call = callRepository.findById(callId).orElse(null);
        if (call == null) {
            log.error("❌ Call 조회 실패 - callId: {}", callId);
            return null;
        }

        CallRecording recording = callRecordingRepository.findByCallId(callId).orElse(null);
        if (recording == null) {
            log.error("❌ CallRecording 조회 실패 - callId: {}", callId);
            return null;
        }

        return new RecordingInfo(
                recording.getFilePath(),
                call.getUser1().getId(),
                call.getUser2().getId()
        );
    }


    /**
     * 로컬 HLS에서 WAV 변환 후 업로드
     */
    private String convertAndUploadWavFromLocal(
            Path localM3u8, Long callId, Long userId, String userLabel) {
        try {
            log.info("🔄 {} WAV 변환 시작 (로컬 파일 사용) - callId: {}, userId: {}",
                    userLabel, callId, userId);

            // 1. 로컬 HLS → WAV 변환
            String outputFileName = String.format("call_%d_%s", callId, userLabel);
            byte[] wavData = audioConverterService.convertLocalHlsToWav(localM3u8, outputFileName);

            // 2. GCS 업로드 경로 생성
            String wavPath = generateWavPath(callId, userId, userLabel);

            // 3. GCS 업로드
            String uploadedUrl = firebaseStorageService.uploadRecordingFile(
                    wavData,
                    wavPath,
                    "audio/wav"
            );

            log.debug("✅ {} WAV 업로드 완료 - callId: {}, url: {}",
                    userLabel, callId, maskUrl(uploadedUrl));

            return uploadedUrl;

        } catch (Exception e) {
            log.error("❌ {} WAV 변환 실패 - callId: {}, userId: {}",
                    userLabel, callId, userId, e);
            throw e;
        }
    }

    /**
     * 임시 디렉토리 정리
     */
    private void cleanupTempDirectory(Path tempDir) {
        try {
            if (Files.exists(tempDir)) {
                Files.walk(tempDir)
                        .sorted(java.util.Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                log.warn("임시 파일 삭제 실패 - {}", path, e);
                            }
                        });
                log.debug("임시 디렉토리 정리 완료 - {}", tempDir);
            }
        } catch (IOException e) {
            log.warn("임시 디렉토리 정리 실패 - {}", tempDir, e);
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

            firebaseStorageService.deleteHlsDirectory(filePath);
            log.info("✅ HLS 원본 삭제 완료 - callId: {}", callId);

        } catch (Exception e) {
            log.warn("⚠️ HLS 원본 삭제 실패 (무시) - callId: {}", callId, e);
        }
    }

    private String maskUrl(String url) {
        if (url == null || url.length() < 30) {
            return "***";
        }
        return url.substring(0, 30) + "...";
    }
}