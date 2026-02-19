package com.ldsilver.chingoohaja.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.matching.enums.QueueStatus;
import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.dto.matching.request.MatchingCancelRequest;
import com.ldsilver.chingoohaja.dto.matching.request.MatchingRequest;
import com.ldsilver.chingoohaja.dto.matching.response.MatchingResponse;
import com.ldsilver.chingoohaja.dto.matching.response.MatchingStatusResponse;
import com.ldsilver.chingoohaja.dto.setting.OperatingHoursInfo;
import com.ldsilver.chingoohaja.infrastructure.jwt.JwtAuthenticationFilter;
import com.ldsilver.chingoohaja.service.MatchingService;
import com.ldsilver.chingoohaja.service.OperatingHoursService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MatchingController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("MatchingController 테스트")
class MatchingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MatchingService matchingService;

    @MockitoBean
    private OperatingHoursService operatingHoursService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private CustomUserDetails userDetails;

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
    @DisplayName("POST /api/v1/calls/match - 매칭 대기열 참가")
    class JoinMatching {

        @Test
        @DisplayName("운영 시간 내에 매칭 대기열에 참가한다")
        void joinMatching_whenOperating_thenReturnsMatchingResponse() throws Exception {
            // given
            given(operatingHoursService.isOperatingTime()).willReturn(true);
            MatchingRequest request = MatchingRequest.of(1L);
            MatchingResponse response = MatchingResponse.waiting(
                    "queue_1_1_abc123", 1L, "일상", 30, 3);
            given(matchingService.joinMatchingQueue(eq(1L), any(MatchingRequest.class)))
                    .willReturn(response);

            // when & then
            mockMvc.perform(post("/api/v1/calls/match")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.queue_id").value("queue_1_1_abc123"))
                    .andExpect(jsonPath("$.data.category_name").value("일상"))
                    .andExpect(jsonPath("$.data.queue_status").value("WAITING"))
                    .andExpect(jsonPath("$.message").value("매칭 대기열 참가 성공"));
        }

        @Test
        @DisplayName("운영 시간 외에는 매칭에 참가할 수 없다")
        void joinMatching_whenNotOperating_thenThrowsException() throws Exception {
            // given
            given(operatingHoursService.isOperatingTime()).willReturn(false);
            given(operatingHoursService.getOperatingHoursInfo())
                    .willReturn(new OperatingHoursInfo(true, "09:00", "23:00"));
            MatchingRequest request = MatchingRequest.of(1L);

            // when & then
            mockMvc.perform(post("/api/v1/calls/match")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isServiceUnavailable());
        }

        @Test
        @DisplayName("카테고리 ID가 없으면 400 에러를 반환한다")
        void joinMatching_whenNoCategoryId_thenReturnsBadRequest() throws Exception {
            // given
            given(operatingHoursService.isOperatingTime()).willReturn(true);
            String invalidRequest = "{}";

            // when & then
            mockMvc.perform(post("/api/v1/calls/match")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidRequest))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/calls/match/status - 매칭 상태 조회")
    class GetMatchingStatus {

        @Test
        @DisplayName("대기열에 있는 경우 매칭 상태를 조회한다")
        void getMatchingStatus_whenInQueue_thenReturnsStatus() throws Exception {
            // given
            MatchingStatusResponse response = MatchingStatusResponse.inQueue(
                    "queue_1_1_abc123", 1L, "일상", QueueStatus.WAITING,
                    30, 3, 10L);
            given(matchingService.getMatchingStatus(1L)).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/calls/match/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.is_in_queue").value(true))
                    .andExpect(jsonPath("$.data.queue_status").value("WAITING"));
        }

        @Test
        @DisplayName("대기열에 없는 경우 상태를 조회한다")
        void getMatchingStatus_whenNotInQueue_thenReturnsNotInQueue() throws Exception {
            // given
            MatchingStatusResponse response = MatchingStatusResponse.notInQueue();
            given(matchingService.getMatchingStatus(1L)).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/calls/match/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.is_in_queue").value(false));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/calls/match - 매칭 취소")
    class CancelMatching {

        @Test
        @DisplayName("매칭을 취소한다")
        void cancelMatching_thenSuccess() throws Exception {
            // given
            MatchingCancelRequest request = MatchingCancelRequest.of("queue_1_1_abc123");
            willDoNothing().given(matchingService).cancelMatching(eq(1L), eq("queue_1_1_abc123"));

            // when & then
            mockMvc.perform(delete("/api/v1/calls/match")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("매칭 취소 성공"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/calls/operating-info - 운영 시간 정보 조회")
    class GetOperatingInfo {

        @Test
        @DisplayName("운영 시간 정보를 조회한다")
        void getOperatingInfo_thenReturnsInfo() throws Exception {
            // given
            OperatingHoursInfo info = new OperatingHoursInfo(true, "09:00", "23:00");
            given(operatingHoursService.getOperatingHoursInfo()).willReturn(info);

            // when & then
            mockMvc.perform(get("/api/v1/calls/operating-info"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.enabled").value(true))
                    .andExpect(jsonPath("$.data.start_time").value("09:00"))
                    .andExpect(jsonPath("$.data.end_time").value("23:00"));
        }
    }
}
