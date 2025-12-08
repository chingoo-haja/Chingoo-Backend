package com.ldsilver.chingoohaja.controller;

import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.dto.common.ApiResponse;
import com.ldsilver.chingoohaja.dto.friendship.response.FriendListResponse;
import com.ldsilver.chingoohaja.service.FriendshipService;
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

    @Operation(
            summary = "친구 요청 전송",
            description = "특정 사용자에게 친구 요청을 전송합니다."
    )
    @PostMapping("/{addresseeId}")
    public ApiResponse<Void> sendFriendRequest(
            @Parameter(description = "친구 요청을 받을 사용자 ID", example = "1")
            @PathVariable Long addresseeId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.debug("친구 요청 전송 - requesterId: {}, addresseeId: {}",
                userDetails.getUserId(), addresseeId);

        friendshipService.sendFriendRequest(userDetails.getUserId(), addresseeId);
        return ApiResponse.ok("친구 요청을 전송했습니다.");
    }

    @Operation(
            summary = "친구 요청 수락",
            description = "받은 친구 요청을 수락합니다. " +
                    "요청을 받은 사람(addressee)만 수락할 수 있습니다."
    )
    @PutMapping("/{friendshipId}/accept")
    public ApiResponse<Void> acceptFriendRequest(
            @Parameter(description = "친구 요청 ID", example = "1")
            @PathVariable Long friendshipId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.debug("친구 요청 수락 - userId: {}, friendshipId: {}",
                userDetails.getUserId(), friendshipId);

        friendshipService.acceptFriendRequest(userDetails.getUserId(), friendshipId);
        return ApiResponse.ok("친구 요청을 수락했습니다.");
    }
}
