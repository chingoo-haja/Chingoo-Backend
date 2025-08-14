package com.ldsilver.chingoohaja.controller;

import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.dto.common.ApiResponse;
import com.ldsilver.chingoohaja.dto.user.response.ProfileResponse;
import com.ldsilver.chingoohaja.service.UserService;
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
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "사용자", description = "사용자 프로필 관리 API")
@SecurityRequirement(name = "Bearer Authentication")
public class UserController {

    private final UserService userService;

    @Operation(
            summary = "내 프로필 조회",
            description = "현재 로그인한 사용자의 프로필 정보를 조회합니다."
    )
    @GetMapping("/profile")
    public ApiResponse<ProfileResponse> getMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.debug("프로필 조회 요청 - userId: {}", userDetails.getUserId());

        ProfileResponse profileResponse = userService.getUserProfile(userDetails.getUserId());
        return ApiResponse.ok("프로필 조회 성공", profileResponse);
    }
}
