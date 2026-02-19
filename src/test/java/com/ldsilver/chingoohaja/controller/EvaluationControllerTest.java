package com.ldsilver.chingoohaja.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldsilver.chingoohaja.domain.evaluation.enums.FeedbackType;
import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.domain.user.enums.UserType;
import com.ldsilver.chingoohaja.dto.evaluation.request.EvaluationRequest;
import com.ldsilver.chingoohaja.dto.evaluation.response.EvaluationResponse;
import com.ldsilver.chingoohaja.dto.evaluation.response.EvaluationStatsResponse;
import com.ldsilver.chingoohaja.infrastructure.jwt.JwtAuthenticationFilter;
import com.ldsilver.chingoohaja.service.EvaluationService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EvaluationController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("EvaluationController 테스트")
class EvaluationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EvaluationService evaluationService;

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
    @DisplayName("POST /api/v1/evaluations - 통화 평가 제출")
    class SubmitEvaluation {

        @Test
        @DisplayName("긍정 평가를 제출한다")
        void submitEvaluation_whenPositive_thenReturnsEvaluation() throws Exception {
            // given
            EvaluationRequest request = EvaluationRequest.positive(1L);
            EvaluationResponse response = new EvaluationResponse(
                    1L, 1L, 1L, 2L, "상대방닉네임", FeedbackType.POSITIVE,
                    LocalDateTime.of(2025, 6, 1, 12, 0));
            given(evaluationService.submitEvaluation(eq(1L), any(EvaluationRequest.class)))
                    .willReturn(response);

            // when & then
            mockMvc.perform(post("/api/v1/evaluations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.feedback_type").value("POSITIVE"))
                    .andExpect(jsonPath("$.data.evaluated_nickname").value("상대방닉네임"));
        }

        @Test
        @DisplayName("부정 평가를 제출한다")
        void submitEvaluation_whenNegative_thenReturnsEvaluation() throws Exception {
            // given
            EvaluationRequest request = EvaluationRequest.negative(1L);
            EvaluationResponse response = new EvaluationResponse(
                    2L, 1L, 1L, 2L, "상대방닉네임", FeedbackType.NEGATIVE,
                    LocalDateTime.of(2025, 6, 1, 12, 0));
            given(evaluationService.submitEvaluation(eq(1L), any(EvaluationRequest.class)))
                    .willReturn(response);

            // when & then
            mockMvc.perform(post("/api/v1/evaluations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.feedback_type").value("NEGATIVE"));
        }

        @Test
        @DisplayName("callId가 없으면 400 에러를 반환한다")
        void submitEvaluation_whenNoCallId_thenReturnsBadRequest() throws Exception {
            // given
            String invalidRequest = "{\"feedback_type\": \"POSITIVE\"}";

            // when & then
            mockMvc.perform(post("/api/v1/evaluations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidRequest))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/evaluations/me/stats - 내 평가 통계 조회")
    class GetMyEvaluationStats {

        @Test
        @DisplayName("내 평가 통계를 조회한다")
        void getMyEvaluationStats_thenReturnsStats() throws Exception {
            // given
            EvaluationStatsResponse stats = new EvaluationStatsResponse(
                    1L, 100, 85, 15, 85.0, 10.0, true,
                    LocalDateTime.of(2025, 6, 1, 12, 0));
            given(evaluationService.getUserEvaluationStats(1L)).willReturn(stats);

            // when & then
            mockMvc.perform(get("/api/v1/evaluations/me/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.total_evaluations").value(100))
                    .andExpect(jsonPath("$.data.positive_rate").value(85.0))
                    .andExpect(jsonPath("$.data.is_top_10_percent").value(true));
        }

        @Test
        @DisplayName("평가가 없는 경우 빈 통계를 반환한다")
        void getMyEvaluationStats_whenNoEvaluations_thenReturnsEmpty() throws Exception {
            // given
            EvaluationStatsResponse emptyStats = EvaluationStatsResponse.noEvaluations(1L);
            given(evaluationService.getUserEvaluationStats(1L)).willReturn(emptyStats);

            // when & then
            mockMvc.perform(get("/api/v1/evaluations/me/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.total_evaluations").value(0));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/evaluations/can-evaluate/{callId} - 평가 가능 여부 확인")
    class CanEvaluate {

        @Test
        @DisplayName("평가 가능한 경우 true를 반환한다")
        void canEvaluate_whenPossible_thenReturnsTrue() throws Exception {
            // given
            given(evaluationService.canEvaluate(1L, 10L)).willReturn(true);

            // when & then
            mockMvc.perform(get("/api/v1/evaluations/can-evaluate/10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").value(true));
        }

        @Test
        @DisplayName("이미 평가한 경우 false를 반환한다")
        void canEvaluate_whenAlreadyEvaluated_thenReturnsFalse() throws Exception {
            // given
            given(evaluationService.canEvaluate(1L, 10L)).willReturn(false);

            // when & then
            mockMvc.perform(get("/api/v1/evaluations/can-evaluate/10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").value(false));
        }
    }
}
