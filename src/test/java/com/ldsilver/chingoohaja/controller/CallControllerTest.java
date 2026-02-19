package com.ldsilver.chingoohaja.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldsilver.chingoohaja.domain.call.enums.CallStatus;
import com.ldsilver.chingoohaja.domain.call.enums.CallType;
import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.dto.call.request.CallStatisticsRequest;
import com.ldsilver.chingoohaja.dto.call.response.CallStatusResponse;
import com.ldsilver.chingoohaja.dto.call.response.TokenRenewResponse;
import com.ldsilver.chingoohaja.infrastructure.jwt.JwtAuthenticationFilter;
import com.ldsilver.chingoohaja.service.AgoraTokenService;
import com.ldsilver.chingoohaja.service.CallStatisticsService;
import com.ldsilver.chingoohaja.service.CallStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CallController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("CallController 테스트")
class CallControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CallStatusService callStatusService;

    @MockitoBean
    private AgoraTokenService agoraTokenService;

    @MockitoBean
    private CallStatisticsService callStatisticsService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private CustomUserDetails userDetails;

    private CallStatusResponse createCallStatusResponse(Long callId, CallStatus status) {
        return new CallStatusResponse(callId, status, CallType.RANDOM_MATCH,
                "일상", "상대방", 2L, "channel_123",
                LocalDateTime.of(2025, 6, 1, 12, 0),
                status == CallStatus.COMPLETED ? LocalDateTime.of(2025, 6, 1, 12, 10) : null,
                status == CallStatus.COMPLETED ? 600 : null,
                LocalDateTime.of(2025, 6, 1, 12, 0),
                false, false);
    }

    @BeforeEach
    void setUp() throws Exception {
        User user = User.ofLocal("test@test.com", "password123!", "testuser", "테스트",
                null, null, null, null);
        Field idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, 1L);
        userDetails = new CustomUserDetails(user);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Nested
    @DisplayName("GET /api/v1/calls/{callId}/status - 통화 상태 조회")
    class GetCallStatus {

        @Test
        @DisplayName("진행 중인 통화 상태를 조회한다")
        void getCallStatus_whenInProgress_thenReturnsStatus() throws Exception {
            // given
            CallStatusResponse response = createCallStatusResponse(1L, CallStatus.IN_PROGRESS);
            given(callStatusService.getCallStatus(1L, 1L)).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/calls/1/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.call_id").value(1))
                    .andExpect(jsonPath("$.data.call_status").value("IN_PROGRESS"))
                    .andExpect(jsonPath("$.data.partner_nickname").value("상대방"));
        }

        @Test
        @DisplayName("완료된 통화 상태를 조회한다")
        void getCallStatus_whenCompleted_thenReturnsStatus() throws Exception {
            // given
            CallStatusResponse response = createCallStatusResponse(1L, CallStatus.COMPLETED);
            given(callStatusService.getCallStatus(1L, 1L)).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/calls/1/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.call_status").value("COMPLETED"))
                    .andExpect(jsonPath("$.data.duration_seconds").value(600));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/calls/{callId}/end - 통화 종료")
    class EndCall {

        @Test
        @DisplayName("통화를 종료한다")
        void endCall_thenReturnsCompletedStatus() throws Exception {
            // given
            CallStatusResponse response = createCallStatusResponse(1L, CallStatus.COMPLETED);
            given(callStatusService.endCall(1L, 1L)).willReturn(response);

            // when & then
            mockMvc.perform(post("/api/v1/calls/1/end"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.call_status").value("COMPLETED"))
                    .andExpect(jsonPath("$.message").value("통화 종료 성공"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/calls/active - 내 활성 통화 조회")
    class GetMyActiveCall {

        @Test
        @DisplayName("활성 통화를 조회한다")
        void getMyActiveCall_thenReturnsActiveCall() throws Exception {
            // given
            CallStatusResponse response = createCallStatusResponse(5L, CallStatus.IN_PROGRESS);
            given(callStatusService.getActiveCallByUserId(1L)).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/calls/active"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.call_id").value(5))
                    .andExpect(jsonPath("$.data.call_status").value("IN_PROGRESS"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/calls/{callId}/renew-token - RTC Token 갱신")
    class RenewToken {

        @Test
        @DisplayName("RTC Token을 갱신한다")
        void renewToken_thenReturnsNewToken() throws Exception {
            // given
            TokenRenewResponse response = TokenRenewResponse.of(
                    "new-rtc-token-123",
                    LocalDateTime.of(2025, 6, 1, 13, 0));
            given(agoraTokenService.renewRtcToken(1L, 1L)).willReturn(response);

            // when & then
            mockMvc.perform(post("/api/v1/calls/1/renew-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.rtcToken").value("new-rtc-token-123"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/calls/{callId}/statistics - 통화 통계 저장")
    class SaveCallStatistics {

        @Test
        @DisplayName("통화 통계를 저장한다")
        void saveCallStatistics_thenReturnsOk() throws Exception {
            // given
            CallStatisticsRequest request = new CallStatisticsRequest(
                    600, 1024000L, 512000L, 128, 64,
                    900000L, 450000L, 4, 3);
            willDoNothing().given(callStatisticsService)
                    .saveCallStatistics(eq(1L), eq(1L), any(CallStatisticsRequest.class));

            // when & then
            mockMvc.perform(post("/api/v1/calls/1/statistics")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("필수 필드가 없으면 400 에러를 반환한다")
        void saveCallStatistics_whenMissingFields_thenReturnsBadRequest() throws Exception {
            // given
            String invalidRequest = "{\"duration\": 600}";

            // when & then
            mockMvc.perform(post("/api/v1/calls/1/statistics")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidRequest))
                    .andExpect(status().isBadRequest());
        }
    }
}
