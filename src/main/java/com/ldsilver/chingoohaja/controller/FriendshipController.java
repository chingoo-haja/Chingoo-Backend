package com.ldsilver.chingoohaja.controller;

import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.dto.common.ApiResponse;
import com.ldsilver.chingoohaja.dto.friendship.request.FriendRequestSendRequest;
import com.ldsilver.chingoohaja.dto.friendship.response.FriendListResponse;
import com.ldsilver.chingoohaja.dto.friendship.response.PendingFriendRequestListResponse;
import com.ldsilver.chingoohaja.dto.friendship.response.SentFriendRequestListResponse;
import com.ldsilver.chingoohaja.service.FriendshipService;
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
@RequestMapping("/api/v1/friendships")
@RequiredArgsConstructor
@Tag(name = "친구", description = "친구 관계 관리 API")
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
            description = "닉네임으로 특정 사용자에게 친구 요청을 전송합니다."
    )
    @PostMapping
    public ApiResponse<Void> sendFriendRequest(
            @Valid @RequestBody FriendRequestSendRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.debug("친구 요청 전송 - requesterId: {}, addresseeNickname: {}",
                userDetails.getUserId(), request.getNickname());

        friendshipService.sendFriendRequest(userDetails.getUserId(), request.getTrimmedNickname());
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

    @Operation(
            summary = "친구 요청 거절",
            description = "받은 친구 요청을 거절합니다. " +
                    "요청을 받은 사람(addressee)만 거절할 수 있습니다."
    )
    @PutMapping("/{friendshipId}/reject")
    public ApiResponse<Void> rejectFriendRequest(
            @Parameter(description = "친구 요청 ID", example = "1")
            @PathVariable Long friendshipId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.debug("친구 요청 거절 - userId: {}, friendshipId: {}",
                userDetails.getUserId(), friendshipId);

        friendshipService.rejectFriendRequest(userDetails.getUserId(), friendshipId);
        return ApiResponse.ok("친구 요청을 거절했습니다.");
    }

    @Operation(
            summary = "받은 친구 요청 목록 조회",
            description = "현재 로그인한 사용자가 받은 친구 요청 목록을 조회합니다. " +
                    "PENDING 상태이면서 내가 응답해야 하는 요청들만 포함됩니다. " +
                    "최신 요청 순으로 정렬됩니다."
    )
    @GetMapping("/requests/received")
    public ApiResponse<PendingFriendRequestListResponse> getPendingFriendRequests(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.debug("받은 친구 요청 목록 조회 요청 - userId: {}", userDetails.getUserId());

        PendingFriendRequestListResponse response =
                friendshipService.getPendingFriendRequests(userDetails.getUserId());
        return ApiResponse.ok("받은 친구 요청 목록 조회 성공", response);
    }

    @Operation(
            summary = "보낸 친구 요청 목록 조회",
            description = "현재 로그인한 사용자가 보낸 친구 요청 목록을 조회합니다. " +
                    "PENDING 상태이면서 상대방의 응답을 기다리는 요청들만 포함됩니다. " +
                    "최신 요청 순으로 정렬됩니다."
    )
    @GetMapping("/requests/sent")
    public ApiResponse<SentFriendRequestListResponse> getSentFriendRequests(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.debug("보낸 친구 요청 목록 조회 요청 - userId: {}", userDetails.getUserId());

        SentFriendRequestListResponse response =
                friendshipService.getSentFriendRequests(userDetails.getUserId());
        return ApiResponse.ok("보낸 친구 요청 목록 조회 성공", response);
    }

    @Operation(
            summary = "보낸 친구 요청 취소",
            description = "내가 보낸 친구 요청을 취소합니다. " +
                    "PENDING 상태이면서 내가 요청 보낸 사람(requester)인 경우만 취소 가능합니다."
    )
    @DeleteMapping("/requests/{friendshipId}")
    public ApiResponse<Void> cancelSentFriendRequest(
            @Parameter(description = "취소할 친구 요청 ID", example = "1")
            @PathVariable Long friendshipId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.debug("보낸 친구 요청 취소 - userId: {}, friendshipId: {}",
                userDetails.getUserId(), friendshipId);

        friendshipService.cancelSentFriendRequest(userDetails.getUserId(), friendshipId);
        return ApiResponse.ok("친구 요청을 취소했습니다.");
    }


    @Operation(
            summary = "친구 삭제",
            description = "친구 관계를 삭제합니다. " +
                    "친구 관계의 양쪽 사용자 모두 삭제할 수 있습니다."
    )
    @DeleteMapping("/{friendId}")
    public ApiResponse<Void> deleteFriendship(
            @Parameter(description = "삭제할 친구 사용자 ID", example = "1")
            @PathVariable Long friendId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.debug("친구 삭제 - userId: {}, friendId: {}",
                userDetails.getUserId(), friendId);

        friendshipService.deleteFriendship(userDetails.getUserId(), friendId);
        return ApiResponse.ok("친구를 삭제했습니다.");
    }

    @Operation(
            summary = "사용자 차단",
            description = "특정 친구 관계를 차단 상태로 변경합니다. " +
                    "PENDING 또는 ACCEPTED 상태에서만 차단할 수 있습니다."
    )
    @PutMapping("/requests/{friendshipId}/block")
    public ApiResponse<Void> blockUser(
            @Parameter(description = "차단할 친구 관계 ID", example = "1")
            @PathVariable Long friendshipId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.debug("사용자 차단 - userId: {}, friendshipId: {}",
                userDetails.getUserId(), friendshipId);

        friendshipService.blockUser(userDetails.getUserId(), friendshipId);
        return ApiResponse.ok("사용자를 차단했습니다.");
    }
}
