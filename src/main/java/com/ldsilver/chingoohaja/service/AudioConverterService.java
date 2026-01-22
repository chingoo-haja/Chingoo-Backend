package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.config.RecordingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioConverterService {

    private final RecordingProperties recordingProperties;

    /**
     * HLS를 WAV로 변환
     */
    public byte[] convertHlsToWav(byte[] hlsData, String outputFileName) {
        log.debug("HLS → WAV 변환 시작 - size: {} bytes", hlsData.length);

        Path tempDir = null;
        Path inputFile = null;
        Path outputFile = null;

        try {
            // 1. 임시 디렉토리 생성
            tempDir = Files.createTempDirectory("audio-convert-");
            inputFile = tempDir.resolve("input.m3u8");
            outputFile = tempDir.resolve(outputFileName + ".wav");

            // 2. HLS 데이터를 임시 파일로 저장
            Files.write(inputFile, hlsData, StandardOpenOption.CREATE);
            log.debug("임시 HLS 파일 생성 - path: {}", inputFile);

            // 3. FFmpeg 명령 실행
            List<String> command = buildFfmpegCommand(inputFile, outputFile);
            executeFfmpeg(command);

            // 4. 변환된 WAV 파일 읽기
            byte[] wavData = Files.readAllBytes(outputFile);

            log.info("HLS → WAV 변환 완료 - inputSize: {} bytes, outputSize: {} bytes",
                    hlsData.length, wavData.length);

            return wavData;

        } catch (IOException e) {
            log.error("❌ 파일 I/O 실패", e);
            throw new CustomException(ErrorCode.FILE_CONVERSION_FAILED,
                    "오디오 변환 중 파일 오류: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("❌ 변환 프로세스 인터럽트", e);
            throw new CustomException(ErrorCode.FILE_CONVERSION_FAILED,
                    "오디오 변환 중단");
        } finally {
            // 5. 임시 파일 정리
            cleanupTempFiles(tempDir, inputFile, outputFile);
        }
    }

    /**
     * FFmpeg 명령 생성
     */
    private List<String> buildFfmpegCommand(Path inputFile, Path outputFile) {
        RecordingProperties.AiTrainingConfig config = recordingProperties.getAiTraining();

        List<String> command = new ArrayList<>();
        command.add("ffmpeg");
        command.add("-i");
        command.add(inputFile.toString());

        // 오디오 코덱 및 설정
        command.add("-acodec");
        command.add("pcm_s16le");  // PCM 16-bit

        // 샘플레이트
        command.add("-ar");
        command.add(String.valueOf(config.getWavSampleRate()));

        // 채널 (Mono/Stereo)
        command.add("-ac");
        command.add(String.valueOf(config.getWavChannels()));

        // 출력 파일
        command.add("-y");  // 덮어쓰기
        command.add(outputFile.toString());

        log.debug("FFmpeg 명령: {}", String.join(" ", command));
        return command;
    }

    /**
     * FFmpeg 프로세스 실행
     */
    private void executeFfmpeg(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        // ✅ FFmpeg 출력을 StringBuilder에 수집
        StringBuilder output = new StringBuilder();

        // FFmpeg 출력 로그
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                log.trace("FFmpeg: {}", line);
            }
        }

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            log.error("FFmpeg 실패 - exit code: {}", exitCode);
            log.error("FFmpeg 출력:\n{}", output);

            throw new CustomException(ErrorCode.FILE_CONVERSION_FAILED,
                    "FFmpeg 실패 - exit code: " + exitCode);
        }

        log.debug("FFmpeg 실행 완료 - exit code: 0");
    }

    /**
     * 임시 파일 정리
     */
    private void cleanupTempFiles(Path tempDir, Path... files) {
        for (Path file : files) {
            if (file != null) {
                try {
                    Files.deleteIfExists(file);
                    log.trace("임시 파일 삭제 - {}", file);
                } catch (IOException e) {
                    log.warn("임시 파일 삭제 실패 - {}", file, e);
                }
            }
        }

        if (tempDir != null) {
            try {
                Files.deleteIfExists(tempDir);
                log.trace("임시 디렉토리 삭제 - {}", tempDir);
            } catch (IOException e) {
                log.warn("임시 디렉토리 삭제 실패 - {}", tempDir, e);
            }
        }
    }

    /**
     * FFmpeg 설치 여부 확인
     */
    public boolean isFfmpegAvailable() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version")
                    .start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            log.warn("FFmpeg 확인 실패", e);
            return false;
        }
    }
}