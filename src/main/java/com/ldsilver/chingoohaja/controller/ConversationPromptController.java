package com.ldsilver.chingoohaja.controller;

import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.dto.call.response.PromptResponse;
import com.ldsilver.chingoohaja.dto.common.ApiResponse;
import com.ldsilver.chingoohaja.service.ConversationPromptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/calls")
@RequiredArgsConstructor
@Tag(name = "통화 질문", description = "통화 중 대화를 돕는 질문 API")
@SecurityRequirement(name = "Bearer Authentication")
public class ConversationPromptController {

    private final ConversationPromptService conversationPromptService;

    @Operation(
            summary = "통화 중 질문 조회",
            description = "통화 중 대화를 돕는 랜덤 질문을 조회합니다. " +
                    "이미 표시된 질문은 제외되며, 카테고리와 난이도 기반 필터링이 가능합니다."
    )
    @GetMapping("/{callId}/prompts")
    public ApiResponse<PromptResponse> getRandomPrompt(
            @Parameter(description = "통화 ID", example = "1")
            @PathVariable Long callId,

            @Parameter(description = "최대 난이도 (1-3)", example = "2")
            @RequestParam(required = false) Integer maxDifficulty,

            @AuthenticationPrincipal CustomUserDetails userDetails) {

        log.debug("통화 질문 조회 - callId: {}, userId: {}", callId, userDetails.getUserId());

        PromptResponse response = conversationPromptService.getRandomPrompt(
                callId,
                userDetails.getUserId(),
                maxDifficulty
        );

        return ApiResponse.ok("질문 조회 성공", response);
    }

    @Operation(
            summary = "질문 피드백 기록",
            description = "표시된 질문이 도움이 되었는지 피드백을 기록합니다. " +
                    "추후 질문 효과 분석에 활용됩니다."
    )
    @PostMapping("/{callId}/prompts/{promptId}/feedback")
    public ApiResponse<Void> recordPromptFeedback(
            @Parameter(description = "통화 ID", example = "1")
            @PathVariable Long callId,

            @Parameter(description = "질문 ID", example = "5")
            @PathVariable Long promptId,

            @Parameter(description = "도움 여부", example = "true")
            @RequestParam boolean helpful,

            @AuthenticationPrincipal CustomUserDetails userDetails) {

        log.debug("질문 피드백 기록 - callId: {}, promptId: {}, helpful: {}",
                callId, promptId, helpful);

        conversationPromptService.recordPromptFeedback(
                callId,
                promptId,
                userDetails.getUserId(),
                helpful
        );

        return ApiResponse.ok("피드백 기록 성공");
    }
}