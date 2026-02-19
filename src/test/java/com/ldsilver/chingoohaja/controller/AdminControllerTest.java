package com.ldsilver.chingoohaja.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldsilver.chingoohaja.domain.call.enums.CallStatus;
import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.domain.user.enums.UserType;
import com.ldsilver.chingoohaja.dto.admin.response.AdminForceEndCallResponse;
import com.ldsilver.chingoohaja.dto.matching.response.*;
import com.ldsilver.chingoohaja.dto.setting.OperatingHoursInfo;
import com.ldsilver.chingoohaja.infrastructure.jwt.JwtAuthenticationFilter;
import com.ldsilver.chingoohaja.service.AdminMatchingService;
import com.ldsilver.chingoohaja.service.CallService;
import com.ldsilver.chingoohaja.service.MatchingStatsService;
import com.ldsilver.chingoohaja.service.OperatingHoursService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("AdminController 테스트")
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OperatingHoursService operatingHoursService;

    @MockitoBean
    private MatchingStatsService matchingStatsService;

    @MockitoBean
    private AdminMatchingService adminMatchingService;

    @MockitoBean
    private CallService callService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private CustomUserDetails adminDetails;

    @BeforeEach
    void setUp() throws Exception {
        User adminUser = User.of("admin@test.com", "admin", "관리자",
                null, null, null, UserType.ADMIN, null, "local", "admin@test.com");
        Field idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(adminUser, 100L);
        adminDetails = new CustomUserDetails(adminUser);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(adminDetails, null, adminDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Nested
    @DisplayName("PUT /api/v1/admin/operating-hours - 운영 시간 변경")
    class UpdateOperatingHours {

        @Test
        @DisplayName("운영 시간을 변경한다")
        void updateOperatingHours_thenSuccess() throws Exception {
            // given
            willDoNothing().given(operatingHoursService).updateOperatingHours("09:00", "23:00");

            // when & then
            mockMvc.perform(put("/api/v1/admin/operating-hours")
                            .param("start_time", "09:00")
                            .param("end_time", "23:00"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("운영 시간 변경 성공"));
        }

        @Test
        @DisplayName("잘못된 시간 형식이면 400 에러를 반환한다")
        void updateOperatingHours_whenInvalidFormat_thenReturnsBadRequest() throws Exception {
            // when & then
            mockMvc.perform(put("/api/v1/admin/operating-hours")
                            .param("start_time", "invalid")
                            .param("end_time", "23:00"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/admin/service-toggle - 서비스 활성화/비활성화")
    class ToggleService {

        @Test
        @DisplayName("서비스를 활성화한다")
        void toggleService_enable_thenSuccess() throws Exception {
            // given
            willDoNothing().given(operatingHoursService).toggleService(true);

            // when & then
            mockMvc.perform(put("/api/v1/admin/service-toggle")
                            .param("enabled", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("서비스 상태 변경 성공"));
        }

        @Test
        @DisplayName("서비스를 비활성화한다")
        void toggleService_disable_thenSuccess() throws Exception {
            // given
            willDoNothing().given(operatingHoursService).toggleService(false);

            // when & then
            mockMvc.perform(put("/api/v1/admin/service-toggle")
                            .param("enabled", "false"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/admin/operating-hours - 현재 운영 시간 조회")
    class GetOperatingHours {

        @Test
        @DisplayName("현재 운영 시간을 조회한다")
        void getOperatingHours_thenReturnsInfo() throws Exception {
            // given
            OperatingHoursInfo info = new OperatingHoursInfo(true, "09:00", "23:00");
            given(operatingHoursService.getOperatingHoursInfo()).willReturn(info);

            // when & then
            mockMvc.perform(get("/api/v1/admin/operating-hours"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.enabled").value(true))
                    .andExpect(jsonPath("$.data.start_time").value("09:00"))
                    .andExpect(jsonPath("$.data.end_time").value("23:00"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/admin/matching/realtime - 실시간 매칭 통계 조회")
    class GetRealtimeStats {

        @Test
        @DisplayName("실시간 매칭 통계를 조회한다")
        void getRealtimeStats_thenReturnsStats() throws Exception {
            // given
            RealtimeMatchingStatsResponse stats = RealtimeMatchingStatsResponse.of(
                    15L, 3, 45.0, 78.5,
                    Collections.emptyList(), Collections.emptyList(),
                    RealtimeMatchingStatsResponse.ServerPerformance.healthy());
            given(matchingStatsService.getRealtimeMatchingStats()).willReturn(stats);

            // when & then
            mockMvc.perform(get("/api/v1/admin/matching/realtime"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.total_waiting_users").value(15))
                    .andExpect(jsonPath("$.data.active_categories").value(3))
                    .andExpect(jsonPath("$.data.matching_success_rate").value(78.5));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/admin/matching/category/{categoryId} - 카테고리별 매칭 통계")
    class GetCategoryStats {

        @Test
        @DisplayName("카테고리별 매칭 통계를 조회한다")
        void getCategoryStats_thenReturnsStats() throws Exception {
            // given
            MatchingStatsResponse stats = MatchingStatsResponse.of(
                    new MatchingStatsResponse.StatsSummary(100L, 80L, 80.0, 30.0, 2400L, 50L, 10, 5.0),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    null, null,
                    new MatchingStatsResponse.PeriodInfo("DAILY", null, null, 1, 100.0));
            given(matchingStatsService.getCategoryMatchingStats(eq(1L), any(), eq(100L)))
                    .willReturn(stats);

            // when & then
            mockMvc.perform(get("/api/v1/admin/matching/category/1")
                            .param("period", "DAILY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.summary.total_matches").value(100));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/admin/matching/cleanup/{categoryId} - 매칭 큐 긴급 정리")
    class CleanupMatchingQueue {

        @Test
        @DisplayName("매칭 큐를 정리한다")
        void cleanupMatchingQueue_thenReturnsResult() throws Exception {
            // given
            MatchingQueueCleanupResponse response = MatchingQueueCleanupResponse.of(
                    1L, "일상", 5L, 3L, 0L, 0L, true, 3);
            given(adminMatchingService.cleanupMatchingQueue(1L)).willReturn(response);

            // when & then
            mockMvc.perform(post("/api/v1/admin/matching/cleanup/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.category_name").value("일상"))
                    .andExpect(jsonPath("$.message").value("매칭 큐 정리 완료"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/admin/matching/health/{categoryId} - 매칭 큐 헬스 체크")
    class CheckMatchingHealth {

        @Test
        @DisplayName("매칭 큐 헬스를 체크한다")
        void checkMatchingHealth_thenReturnsHealth() throws Exception {
            // given
            MatchingQueueHealthResponse response = MatchingQueueHealthResponse.of(
                    1L, "일상", true,
                    5L, true,
                    5L, 0L, true, 0L,
                    MatchingQueueHealthResponse.HealthStatus.HEALTHY,
                    Collections.emptyList());
            given(adminMatchingService.checkMatchingHealth(1L)).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/admin/matching/health/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.health_status").value("HEALTHY"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/admin/calls/{callId}/force-end - 통화 강제 종료")
    class ForceEndCall {

        @Test
        @DisplayName("통화를 강제 종료한다")
        void forceEndCall_thenReturnsResult() throws Exception {
            // given
            AdminForceEndCallResponse response = new AdminForceEndCallResponse(
                    1L, CallStatus.IN_PROGRESS, CallStatus.COMPLETED,
                    "사용자1", "사용자2", "일상",
                    LocalDateTime.of(2025, 6, 1, 12, 0),
                    LocalDateTime.of(2025, 6, 1, 12, 30),
                    1800,
                    LocalDateTime.of(2025, 6, 1, 12, 30),
                    100L, "관리자에 의해 강제 종료됨");
            given(callService.forceEndCallByAdmin(1L, 100L)).willReturn(response);

            // when & then
            mockMvc.perform(post("/api/v1/admin/calls/1/force-end"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.call_id").value(1))
                    .andExpect(jsonPath("$.data.previous_status").value("IN_PROGRESS"))
                    .andExpect(jsonPath("$.data.current_status").value("COMPLETED"))
                    .andExpect(jsonPath("$.message").value("통화가 강제 종료되었습니다."));
        }
    }
}
