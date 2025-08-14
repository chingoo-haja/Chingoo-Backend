package com.ldsilver.chingoohaja.controller;

import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.dto.common.ApiResponse;
import com.ldsilver.chingoohaja.dto.user.request.ProfileImageUploadRequest;
import com.ldsilver.chingoohaja.dto.user.request.ProfileUpdateRequest;
import com.ldsilver.chingoohaja.dto.user.response.ProfileImageUploadResponse;
import com.ldsilver.chingoohaja.dto.user.response.ProfileResponse;
import com.ldsilver.chingoohaja.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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


    @Operation(
            summary = "내 프로필 수정",
            description = "현재 로그인한 사용자의 프로필 정보를 수정합니다." +
                    "닉네임은 중복 검증을 거쳐 변경됩니다."
    )
    @PutMapping("/profile")
    public ApiResponse<ProfileResponse> updateMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ProfileUpdateRequest request) {
        log.debug("프로필 수정 요청 - userId: {}, nickname: {}", userDetails.getUserId(), request.getNickname());

        ProfileResponse updateProfile = userService.updateUserProfile(
                userDetails.getUserId(), request
        );

        return ApiResponse.ok("프로필 수정 성공", updateProfile);
    }

    @Operation(
            summary = "프로필 이미지 수정 업로드",
            description = "현재 로그인한 사용자의 프로필 이미지를 업로드합니다." +
                    "지원 형식: JPEG, PNG, WebP / 최대 크기: 5MB"
    )
    @PostMapping(value = "/profile/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ProfileImageUploadResponse> uploadProfileImage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("image")MultipartFile image) {
        log.debug("프로필 이미지 업로드 요청 - userId: {}", userDetails.getUserId());

        ProfileImageUploadRequest request = ProfileImageUploadRequest.from(image);
        ProfileImageUploadResponse response = userService.updateProfileImage(
                userDetails.getUserId(), request
        );
        return ApiResponse.ok("프로필 이미지 업로드 성공", response);
    }
}
