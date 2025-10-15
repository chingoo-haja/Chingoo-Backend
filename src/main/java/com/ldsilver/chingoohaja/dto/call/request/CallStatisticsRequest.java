package com.ldsilver.chingoohaja.dto.call.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "통화 통계 정보 요청")
public record CallStatisticsRequest(
        @Schema(description = "통화 시간 (초)", example = "120")
        @NotNull(message = "통화 시간은 필수입니다.")
        @Min(value = 0, message = "통화 시간은 0 이상이어야 합니다.")
        Integer duration,

        @Schema(description = "전송한 바이트 (bytes)", example = "524288")
        @NotNull(message = "전송 바이트는 필수입니다.")
        @Min(value = 0, message = "전송 바이트는 0 이상이어야 합니다.")
        Long sendBytes,

        @Schema(description = "수신한 바이트 (bytes)", example = "524288")
        @NotNull(message = "수신 바이트는 필수입니다.")
        @Min(value = 0, message = "수신 바이트는 0 이상이어야 합니다.")
        Long receiveBytes,

        @Schema(description = "전송 비트레이트 (kbps)", example = "48")
        @NotNull(message = "전송 비트레이트는 필수입니다.")
        @Min(value = 0, message = "전송 비트레이트는 0 이상이어야 합니다.")
        Integer sendBitrate,

        @Schema(description = "수신 비트레이트 (kbps)", example = "48")
        @NotNull(message = "수신 비트레이트는 필수입니다.")
        @Min(value = 0, message = "수신 비트레이트는 0 이상이어야 합니다.")
        Integer receiveBitrate,

        @Schema(description = "오디오 전송 바이트 (bytes)", example = "262144")
        @NotNull(message = "오디오 전송 바이트는 필수입니다.")
        @Min(value = 0, message = "오디오 전송 바이트는 0 이상이어야 합니다.")
        Long audioSendBytes,

        @Schema(description = "오디오 수신 바이트 (bytes)", example = "262144")
        @NotNull(message = "오디오 수신 바이트는 필수입니다.")
        @Min(value = 0, message = "오디오 수신 바이트는 0 이상이어야 합니다.")
        Long audioReceiveBytes,

        @Schema(description = "업링크 네트워크 품질 (1~6, 1:최상, 6:최악)", example = "2")
        @NotNull(message = "업링크 네트워크 품질은 필수입니다.")
        @Min(value = 1, message = "네트워크 품질은 1 이상이어야 합니다.")
        @Max(value = 6, message = "네트워크 품질은 6 이하여야 합니다.")
        Integer uplinkNetworkQuality,

        @Schema(description = "다운링크 네트워크 품질 (1~6, 1:최상, 6:최악)", example = "2")
        @NotNull(message = "다운링크 네트워크 품질은 필수입니다.")
        @Min(value = 1, message = "네트워크 품질은 1 이상이어야 합니다.")
        @Max(value = 6, message = "네트워크 품질은 6 이하여야 합니다.")
        Integer downlinkNetworkQuality
) {
    /**
     * 평균 네트워크 품질 계산
     */
    public double getAverageNetworkQuality() {
        return (uplinkNetworkQuality + downlinkNetworkQuality) / 2.0;
    }

    /**
     * 네트워크 품질을 문자열로 변환
     * 1: Excellent, 2: Good, 3: Poor, 4: Bad, 5: Very Bad, 6: Down
     */
    public String getNetworkQualityDescription() {
        double avg = getAverageNetworkQuality();
        if (avg <= 1.5) return "EXCELLENT";
        if (avg <= 2.5) return "GOOD";
        if (avg <= 3.5) return "POOR";
        if (avg <= 4.5) return "BAD";
        if (avg <= 5.5) return "VERY_BAD";
        return "DOWN";
    }

    /**
     * 총 데이터 사용량 (MB)
     */
    public double getTotalDataUsageMB() {
        return (sendBytes + receiveBytes) / (1024.0 * 1024.0);
    }
}