package com.ldsilver.chingoohaja.dto.report.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.domain.report.enums.ReportReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "사용자 신고 요청")
public record ReportUserRequest(
        @Schema(description = "통화 ID (선택)", example = "123")
        @JsonProperty("call_id")
        Long callId,

        @Schema(description = "신고 사유", example = "INAPPROPRIATE_LANGUAGE")
        @NotNull(message = "신고 사유는 필수입니다.")
        @JsonProperty("reason")
        ReportReason reason,

        @Schema(description = "상세 내용 (선택)", example = "욕설을 사용했습니다.")
        @Size(max = 500, message = "상세 내용은 500자를 초과할 수 없습니다.")
        @JsonProperty("details")
        String details
) {
}