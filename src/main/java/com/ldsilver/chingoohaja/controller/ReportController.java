package com.ldsilver.chingoohaja.controller;

import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.dto.common.ApiResponse;
import com.ldsilver.chingoohaja.dto.report.request.ReportUserRequest;
import com.ldsilver.chingoohaja.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;



@Slf4j
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "신고", description = "사용자 신고 API")
@SecurityRequirement(name = "Bearer Authentication")
public class ReportController {

    private final ReportService reportService;

    @Operation(
            summary = "사용자 신고",
            description = "통화 상대방을 신고합니다. 신고된 사용자와는 더 이상 매칭되지 않습니다."
    )
    @PostMapping("/users/{reportedUserId}")
    public ApiResponse<Void> reportUser(
            @Parameter(description = "신고할 사용자 ID", example = "1")
            @PathVariable Long reportedUserId,

            @Valid @RequestBody ReportUserRequest request,

            @AuthenticationPrincipal CustomUserDetails userDetails) {

        log.debug("사용자 신고 요청 - reporterId: {}, reportedId: {}, reason: {}",
                userDetails.getUserId(), reportedUserId, request.reason());

        reportService.reportUser(userDetails.getUserId(), reportedUserId, request);

        return ApiResponse.ok("신고가 접수되었습니다.");
    }
}
