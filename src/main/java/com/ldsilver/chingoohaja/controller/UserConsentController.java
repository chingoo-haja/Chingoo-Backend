package com.ldsilver.chingoohaja.controller;

import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.dto.common.ApiResponse;
import com.ldsilver.chingoohaja.dto.user.request.ConsentWithdrawRequest;
import com.ldsilver.chingoohaja.dto.user.request.UserConsentRequest;
import com.ldsilver.chingoohaja.dto.user.response.UserConsentResponse;
import com.ldsilver.chingoohaja.dto.user.response.UserConsentsResponse;
import com.ldsilver.chingoohaja.service.UserConsentService;
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
@RequestMapping("/api/v1/users/consents")
@RequiredArgsConstructor
@Tag(name = "사용자 동의", description = "개인정보 수집·이용 동의 관리 API")
@SecurityRequirement(name = "Bearer Authentication")
public class UserConsentController {

    private final UserConsentService userConsentService;

    @Operation(
            summary = "동의 정보 저장",
            description = "회원가입 시 또는 최초 진입 시 필수/선택 동의 정보를 저장합니다. " +
                    "필수 동의는 반드시 true여야 하며, 선택 동의는 사용자가 체크한 경우에만 저장됩니다."
    )
    @PostMapping
    public ApiResponse<Void> saveConsents(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UserConsentRequest request) {
        log.debug("동의 정보 저장 요청 - userId: {}", userDetails.getUserId());

        userConsentService.saveConsents(userDetails.getUserId(), request);

        return ApiResponse.ok("동의 정보가 저장되었습니다.");
    }

    @Operation(
            summary = "내 동의 정보 조회",
            description = "현재 로그인한 사용자의 모든 동의 정보를 조회합니다."
    )
    @GetMapping
    public ApiResponse<UserConsentsResponse> getMyConsents(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.debug("동의 정보 조회 요청 - userId: {}", userDetails.getUserId());

        UserConsentsResponse response = userConsentService.getUserConsents(userDetails.getUserId());

        return ApiResponse.ok("동의 정보 조회 성공", response);
    }

    @Operation(
            summary = "동의 철회",
            description = "선택 동의를 철회합니다. 필수 동의는 철회할 수 없습니다."
    )
    @PostMapping("/withdraw")
    public ApiResponse<UserConsentResponse> withdrawConsent(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ConsentWithdrawRequest request) {
        log.debug("동의 철회 요청 - userId: {}, consentType: {}",
                userDetails.getUserId(), request.getConsentType());

        UserConsentResponse response = userConsentService.withdrawConsent(
                userDetails.getUserId(), request);

        return ApiResponse.ok("동의가 철회되었습니다.", response);
    }
}