package com.ldsilver.chingoohaja.controller;

import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.dto.common.ApiResponse;
import com.ldsilver.chingoohaja.dto.matching.request.MatchingRequest;
import com.ldsilver.chingoohaja.dto.matching.response.MatchingResponse;
import com.ldsilver.chingoohaja.dto.matching.response.MatchingStatusResponse;
import com.ldsilver.chingoohaja.service.MatchingService;
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
@RequestMapping("/api/v1/calls")
@RequiredArgsConstructor
@Tag(name = "매칭", description = "실시간 매칭 API")
@SecurityRequirement(name = "Bearer Authentication")
public class MatchingController {
    private final MatchingService matchingService;

    @Operation(
            summary = "매칭 대기열 참가",
            description = "선택한 카테고리의 매칭 대기열에 참가합니다." +
                    "같은 카테고리를 선택한 다른 사용자와 자동으로 매칭됩니다."
    )
    @PostMapping("/match")
    public ApiResponse<MatchingResponse> joinMatching(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody MatchingRequest request) {
        log.debug("매칭 참가 요청 - userId: {}, categoryId: {}",
                userDetails.getUserId(), request.categoryId());

        MatchingResponse response = matchingService.joinMatchingQueue(
                userDetails.getUserId(),
                request
        );
        return ApiResponse.ok("매칭 대기열 참가 성공", response);
    }


    @Operation(
            summary = "매칭 상태 조회",
            description = "현재 사용자의 매칭 대기 상태를 조회합니다." +
                    "대기 중이 아닌경우 is_in_queue가 false로 반환됩니다."
    )
    @GetMapping("/match/status")
    public ApiResponse<MatchingStatusResponse> getMatchingStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.debug("매칭 상태 조회 요청 - userId: {}", userDetails.getUserId());

        MatchingStatusResponse response = matchingService.getMatchingStatus(userDetails.getUserId());

        return ApiResponse.ok("매칭 상태 조회 성공", response);
    }
}
