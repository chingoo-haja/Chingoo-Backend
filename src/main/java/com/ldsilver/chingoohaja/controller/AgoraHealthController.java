package com.ldsilver.chingoohaja.controller;

import com.ldsilver.chingoohaja.dto.call.AgoraHealthStatus;
import com.ldsilver.chingoohaja.dto.common.ApiResponse;
import com.ldsilver.chingoohaja.service.AgoraService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/agora")
@RequiredArgsConstructor
@Tag(name = "Agora 상태", description = "Agora 서비스 상태 확인 API")
public class AgoraHealthController {

    private final AgoraService agoraService;

    @Operation(
            summary = "Agora 서비스 헬스체크",
            description = "Agora 토큰 생성, REST API 연결 상태를 확인합니다. " +
                    "시스템 관리자가 서비스 상태를 모니터링할 때 사용합니다."
    )
    @GetMapping("/health")
    public ApiResponse<AgoraHealthStatus> checkAgoraHealth() {
        log.debug("Agora 헬스체크 요청");

        AgoraHealthStatus healthStatus = agoraService.checkHealth();

        String message = healthStatus.isHealthy() ?
                "Agora 서비스 상태 정상" :
                "Agora 서비스 상태 점검 필요";

        return ApiResponse.ok(message, healthStatus);
    }

    @Operation(
            summary = "Agora 서비스 재초기화",
            description = "Agora 서비스 연결을 다시 시도합니다. " +
                    "연결 문제가 발생했을 때 관리자가 수동으로 재시도할 수 있습니다."
    )
    @GetMapping("/reinitialize")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AgoraHealthStatus> reinitializeAgora() {
        log.info("Agora 서비스 재초기화 요청");

        boolean initResult = agoraService.initializeAndTest();
        AgoraHealthStatus healthStatus = agoraService.checkHealth();

        String message = initResult ?
                "Agora 서비스 재초기화 성공" :
                "Agora 서비스 재초기화 부분 성공 (일부 기능 제한)";

        return ApiResponse.ok(message, healthStatus);
    }
}
