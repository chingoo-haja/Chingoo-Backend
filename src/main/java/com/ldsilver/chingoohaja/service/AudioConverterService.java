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
    public byte[] convertLocalHlsToWav(Path localM3u8Path, String outputFileName) {
        log.debug("로컬 HLS → WAV 변환 시작 - path: {}", localM3u8Path);

        Path outputFile = null;

        try {
            // 1. 출력 파일 경로 (플레이리스트와 같은 디렉토리)
            Path tempDir = localM3u8Path.getParent();
            outputFile = tempDir.resolve(outputFileName + ".wav");

            // 2. FFmpeg 명령 실행
            List<String> command = buildFfmpegCommand(localM3u8Path, outputFile);
            executeFfmpeg(command);

            // 3. 변환된 WAV 파일 읽기
            byte[] wavData = Files.readAllBytes(outputFile);

            log.debug("✅ 로컬 HLS → WAV 변환 완료 - outputSize: {} bytes", wavData.length);

            return wavData;

        } catch (IOException e) {
            log.error("❌ 파일 I/O 실패", e);
            throw new CustomException(ErrorCode.FILE_CONVERSION_FAILED,
                    "오디오 변환 중 파일 오류: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("❌ 변환 프로세스 인터럽트", e);
            throw new CustomException(ErrorCode.FILE_CONVERSION_FAILED, "오디오 변환 중단");
        } finally {
            // 출력 파일만 삭제 (세그먼트는 나중에 일괄 삭제)
            if (outputFile != null) {
                try {
                    Files.deleteIfExists(outputFile);
                } catch (IOException e) {
                    log.warn("출력 파일 삭제 실패 - {}", outputFile, e);
                }
            }
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

}