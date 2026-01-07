package com.ldsilver.chingoohaja.dto.setting;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.time.ZoneId;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "운영 시간 정보")
public class OperatingHoursInfo {

    @Schema(description = "서비스 활성화 여부", example = "true")
    @JsonProperty("enabled")
    private boolean enabled;

    @Schema(description = "운영 시작 시간", example = "09:00")
    @JsonProperty("start_time")
    private String startTime;

    @Schema(description = "운영 종료 시간", example = "23:00")
    @JsonProperty("end_time")
    private String endTime;

    @Schema(description = "현재 운영 중 여부", example = "true")
    @JsonProperty("is_operating")
    public boolean isCurrentlyOperating() {
        if (!enabled) {
            return false;
        }

        LocalTime now = LocalTime.now(ZoneId.of("Asia/Seoul"));
        LocalTime start = LocalTime.parse(startTime);
        LocalTime end = LocalTime.parse(endTime);

        return !now.isBefore(start) && now.isBefore(end);
    }

    @Schema(description = "운영 시간 텍스트", example = "09:00 ~ 23:00")
    @JsonProperty("operating_hours_text")
    public String getOperatingHoursText() {
        return String.format("%s ~ %s", startTime, endTime);
    }
}