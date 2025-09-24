package com.ldsilver.chingoohaja.controller;

import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.dto.common.ApiResponse;
import com.ldsilver.chingoohaja.dto.evaluation.request.EvaluationRequest;
import com.ldsilver.chingoohaja.dto.evaluation.response.EvaluationResponse;
import com.ldsilver.chingoohaja.dto.evaluation.response.EvaluationStatsResponse;
import com.ldsilver.chingoohaja.service.EvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

    @Operation(
            summary = "내 평가 통계 조회",
            description = "현재 사용자가 받은 평가 통계를 조회합니다. " +
                    "이번 달 기준으로 긍정/부정 평가 수, 순위 백분율 등을 제공합니다."
    )
    @GetMapping("/me/stats")
    public ApiResponse<EvaluationStatsResponse> getMyEvaluationStats(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        log.debug("내 평가 통계 조회 - userId: {}", userDetails.getUserId());

        EvaluationStatsResponse response = evaluationService.getUserEvaluationStats(
                userDetails.getUserId());

        return ApiResponse.ok("평가 통계 조회 성공", response);
    }

    @Operation(
            summary = "평가 가능 여부 확인",
            description = "특정 통화에 대해 평가할 수 있는지 여부를 확인합니다. " +
                    "통화가 완료되었고, 아직 평가하지 않은 경우에만 true를 반환합니다."
    )
    @GetMapping("/can-evaluate/{callId}")
    public ApiResponse<Boolean> canEvaluate(
            @Parameter(description = "통화 ID", example = "1")
            @PathVariable Long callId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        log.debug("평가 가능 여부 확인 - userId: {}, callId: {}",
                userDetails.getUserId(), callId);

        boolean canEvaluate = evaluationService.canEvaluate(
                userDetails.getUserId(), callId);

        return ApiResponse.ok("평가 가능 여부 확인 완료", canEvaluate);
    }
}
