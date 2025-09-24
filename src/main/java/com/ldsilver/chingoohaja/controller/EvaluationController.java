package com.ldsilver.chingoohaja.controller;

import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.dto.common.ApiResponse;
import com.ldsilver.chingoohaja.dto.evaluation.request.EvaluationRequest;
import com.ldsilver.chingoohaja.dto.evaluation.response.EvaluationResponse;
import com.ldsilver.chingoohaja.service.EvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/evaluations")
@RequiredArgsConstructor
@Tag(name = "평가", description = "통화 평가 관리 API")
@SecurityRequirement(name = "Bearer Authentication")
public class EvaluationController {

    private final EvaluationService evaluationService;

    @Operation(
            summary = "통화 평가 제출",
            description = "완료된 통화에 대해 상대방을 평가합니다. " +
                    "통화 참가자만 평가할 수 있으며, 한 번만 평가 가능합니다."
    )
    @PostMapping
    public ApiResponse<EvaluationResponse> submitEvaluation(
            @Valid @RequestBody EvaluationRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        log.debug("평가 제출 요청 - userId: {}, callId: {}, type: {}",
                userDetails.getUserId(), request.callId(), request.feedbackType());

        EvaluationResponse response = evaluationService.submitEvaluation(
                userDetails.getUserId(), request);

        return ApiResponse.ok("평가가 완료되었습니다.", response);
    }
}
