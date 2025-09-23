package com.ldsilver.chingoohaja.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.recording")
public class RecordingProperties {

    /**
     * 녹음 기본 설정
     */
    @Min(value = 10, message = "최대 유휴 시간은 최소 10초 이상이어야 합니다.")
    @Max(value = 300, message = "최대 유휴 시간은 최대 300초 이하여야 합니다.") //5분
    private int maxIdleTime = 300; // 기본값

    /**
     * 오디오 프로필 설정
     * 0: 48kHz, 48kbps, mono, LC-AAC (기본값 - 비용 최적화)
     * 1: 48kHz, 128kbps, mono, LC-AAC
     * 2: 48kHz, 192kbps, stereo, LC-AAC (고음질)
     */
    @Min(value = 0, message = "오디오 프로필은 0-2 범위여야 합니다.")
    @Max(value = 2, message = "오디오 프로필은 0-2 범위여야 합니다.")
    private int audioProfile = 0;

    /**
     * 오디오만 녹음 (비용 절약)
     */
    private final boolean audioOnly = true;

    /**
     * 자동 녹음 시작 (통화 시작 시)
     */
    private boolean autoStart = false;

    /**
     * 자동 녹음 중지 (통화 종료 시)
     */
    private boolean autoStop = true;

    /**
     * 파일 이름 접두어
     */
    private String fileNamePrefix = "call_recording";

    /**
     * 녹음 파일 포맷 (hls, mp3, mp4)
     */
    private String[] fileFormats = {"hls"};

    /**
     * 정리 스케줄러 활성화
     */
    private CleanupConfig cleanup = new CleanupConfig();

    private boolean useCustomStorage = false;

    @Getter
    @Setter
    public static class CleanupConfig {
        private boolean enabled = true;

        /**
         * 고아 녹음 정리 주기 (밀리초)
         */
        private long orphanCleanupDelay = 600000L; // 10분

        /**
         * 장시간 녹음 체크 주기 (밀리초)
         */
        private long longRunningCheckDelay = 3600000L; // 1시간

        /**
         * 장시간 녹음 임계값 (시간)
         */
        private int longRunningThresholdHours = 2;

        /**
         * 실패한 녹음 재시도 주기 (밀리초)
         */
        private long retryFailedDelay = 1800000L; // 30분
    }

    /**
     * Agora API용 채널 타입 반환
     */
    public int getChannelType() {
        return 0; // 0: 통신 모드 (음성통화용)
    }

    /**
     * Agora API용 스트림 타입 반환
     */
    public int getStreamTypes() {
        return 0; // 0: 오디오만, 2: 오디오+비디오
    }

    /**
     * 현재 설정으로 예상 비용 등급 반환 (참고용)
     */
    public String getCostLevel() {
        return "MINIMUM";
    }

    /**
     * 설정 유효성 검증
     */
    public boolean isValidConfiguration() {
        return maxIdleTime >= 10 && maxIdleTime <= 300 &&
                audioProfile >= 0 && audioProfile <= 2 &&
                fileFormats != null && fileFormats.length > 0;
    }

    @AssertTrue(message = "fileFormats는 hls/mp3/mp4만 허용됩니다.")
    public boolean areFileFormatsValid() {
        if (fileFormats == null || fileFormats.length == 0) return false;
        java.util.Set<String> allowed = java.util.Set.of("hls","mp3","mp4");
        for (String f : fileFormats) {
            if (f == null || !allowed.contains(f.toLowerCase())) return false;
        }
        return true;
    }
}
