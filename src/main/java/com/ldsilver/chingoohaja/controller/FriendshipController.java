package com.ldsilver.chingoohaja.controller;

import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.dto.common.ApiResponse;
import com.ldsilver.chingoohaja.dto.friendship.response.FriendListResponse;
import com.ldsilver.chingoohaja.service.FriendshipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/friendships")
@RequiredArgsConstructor
@Tag(name = "친구", description = "친구 관게 관리 API")
@SecurityRequirement(name = "Bearer Authentication")
public class FriendshipController {

    private final FriendshipService friendshipService;

    @Operation(
            summary = "친구 목록 조회",
            description = "현재 로그인한 사용자의 친구 목록을 조회합니다. " +
                    "마지막 통화 시간 기준으로 내림차순 정렬됩니다."
    )
    @GetMapping
    public ApiResponse<FriendListResponse> getFriendsList(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.debug("친구 목록 조회 요청 - userId: {}", userDetails.getUserId());

        FriendListResponse response = friendshipService.getFriendsList(userDetails.getUserId());
        return ApiResponse.ok("친구 목록 조회 성공", response);
    }
}
