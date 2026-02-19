package com.ldsilver.chingoohaja.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.domain.user.enums.Gender;
import com.ldsilver.chingoohaja.dto.user.request.ProfileUpdateRequest;
import com.ldsilver.chingoohaja.dto.user.response.CallHistoryResponse;
import com.ldsilver.chingoohaja.dto.user.response.ProfileImageUploadResponse;
import com.ldsilver.chingoohaja.dto.user.response.ProfileResponse;
import com.ldsilver.chingoohaja.dto.user.response.UserActivityStatsResponse;
import com.ldsilver.chingoohaja.infrastructure.jwt.JwtAuthenticationFilter;
import com.ldsilver.chingoohaja.service.CallHistoryService;
import com.ldsilver.chingoohaja.service.UserActivityStatsService;
import com.ldsilver.chingoohaja.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("UserController 테스트")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserActivityStatsService userActivityStatsService;

    @MockitoBean
    private CallHistoryService callHistoryService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() throws Exception {
        User user = User.ofLocal("test@test.com", "password123!", "testuser", "테스트유저",
                Gender.MALE, LocalDate.of(2000, 1, 1), "010-1234-5678", null);
        Field idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, 1L);
        userDetails = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Nested
    @DisplayName("GET /api/v1/users/profile - 내 프로필 조회")
    class GetMyProfile {

        @Test
        @DisplayName("내 프로필을 조회한다")
        void getMyProfile_thenReturnsProfile() throws Exception {
            // given
            ProfileResponse profile = new ProfileResponse(
                    1L, "test@test.com", "testuser", "테스트유저",
                    "MALE", LocalDate.of(2000, 1, 1), 25,
                    "010-1234-5678", "USER", null,
                    "local", true, "테스트유저",
                    LocalDateTime.of(2025, 1, 1, 0, 0),
                    LocalDateTime.of(2025, 6, 1, 0, 0));
            given(userService.getUserProfile(1L)).willReturn(profile);

            // when & then
            mockMvc.perform(get("/api/v1/users/profile")
)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.email").value("test@test.com"))
                    .andExpect(jsonPath("$.data.nickname").value("testuser"))
                    .andExpect(jsonPath("$.message").value("프로필 조회 성공"));
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/users/profile - 내 프로필 수정")
    class UpdateMyProfile {

        @Test
        @DisplayName("닉네임을 수정한다")
        void updateMyProfile_whenChangeNickname_thenReturnsUpdatedProfile() throws Exception {
            // given
            ProfileUpdateRequest request = new ProfileUpdateRequest(
                    null, "새닉네임", null, null, null);
            ProfileResponse updatedProfile = new ProfileResponse(
                    1L, "test@test.com", "새닉네임", "테스트유저",
                    "MALE", LocalDate.of(2000, 1, 1), 25,
                    "010-1234-5678", "USER", null,
                    "local", true, "테스트유저",
                    LocalDateTime.of(2025, 1, 1, 0, 0),
                    LocalDateTime.of(2025, 6, 1, 12, 0));
            given(userService.updateUserProfile(eq(1L), any(ProfileUpdateRequest.class)))
                    .willReturn(updatedProfile);

            // when & then
            mockMvc.perform(put("/api/v1/users/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.nickname").value("새닉네임"))
                    .andExpect(jsonPath("$.message").value("프로필 수정 성공"));
        }

        @Test
        @DisplayName("변경 사항이 없으면 400 에러를 반환한다")
        void updateMyProfile_whenNoChange_thenReturnsBadRequest() throws Exception {
            // given - 모든 필드가 null인 요청
            String emptyRequest = "{}";

            // when & then
            mockMvc.perform(put("/api/v1/users/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(emptyRequest))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/users/profile/image - 프로필 이미지 업로드")
    class UploadProfileImage {

        @Test
        @DisplayName("프로필 이미지를 업로드한다")
        void uploadProfileImage_thenReturnsUploadResult() throws Exception {
            // given
            MockMultipartFile imageFile = new MockMultipartFile(
                    "image", "profile.jpg", MediaType.IMAGE_JPEG_VALUE,
                    "fake image data".getBytes());
            ProfileImageUploadResponse response = ProfileImageUploadResponse.of(
                    "https://storage.example.com/profile/1.jpg", "profile.jpg", 1024L);
            given(userService.updateProfileImage(eq(1L), any())).willReturn(response);

            // when & then
            mockMvc.perform(multipart("/api/v1/users/profile/image")
                            .file(imageFile)
)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.profile_image_url").value("https://storage.example.com/profile/1.jpg"))
                    .andExpect(jsonPath("$.message").value("프로필 이미지 업로드 성공"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users/me/activity-stats - 내 활동 통계 조회")
    class GetMyActivityStats {

        @Test
        @DisplayName("주간 활동 통계를 조회한다")
        void getMyActivityStats_whenWeekly_thenReturnsStats() throws Exception {
            // given
            UserActivityStatsResponse stats = UserActivityStatsResponse.of(
                    new UserActivityStatsResponse.WeeklyStats(5, 120,
                            LocalDate.of(2025, 5, 26), LocalDate.of(2025, 6, 1)),
                    null, null);
            given(userActivityStatsService.getUserActivityStats(1L, "week")).willReturn(stats);

            // when & then
            mockMvc.perform(get("/api/v1/users/me/activity-stats")
                            .param("period", "week")
)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("활동 통계 조회 성공"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users/me/call-history - 내 통화 이력 조회")
    class GetMyCallHistory {

        @Test
        @DisplayName("통화 이력을 조회한다")
        void getMyCallHistory_thenReturnsHistory() throws Exception {
            // given
            CallHistoryResponse.Pagination pagination = new CallHistoryResponse.Pagination(
                    1, 1, 1, false);
            List<CallHistoryResponse.CallHistoryItem> calls = List.of(
                    new CallHistoryResponse.CallHistoryItem(
                            1L, 2L, "상대방", 1L, "일상",
                            LocalDateTime.of(2025, 6, 1, 12, 0),
                            LocalDateTime.of(2025, 6, 1, 12, 10),
                            10, 4.0, 15.5)
            );
            CallHistoryResponse response = CallHistoryResponse.of(calls, pagination);
            given(callHistoryService.getCallHistory(1L, 1, 20, "all")).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/users/me/call-history")
)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.calls.length()").value(1))
                    .andExpect(jsonPath("$.data.calls[0].partner_nickname").value("상대방"))
                    .andExpect(jsonPath("$.message").value("통화 이력 조회 성공"));
        }

        @Test
        @DisplayName("빈 통화 이력을 조회한다")
        void getMyCallHistory_whenEmpty_thenReturnsEmptyList() throws Exception {
            // given
            CallHistoryResponse.Pagination pagination = new CallHistoryResponse.Pagination(
                    1, 0, 0, false);
            CallHistoryResponse response = CallHistoryResponse.of(Collections.emptyList(), pagination);
            given(callHistoryService.getCallHistory(1L, 1, 20, "all")).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/users/me/call-history")
)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.calls").isArray())
                    .andExpect(jsonPath("$.data.calls.length()").value(0));
        }

        @Test
        @DisplayName("페이지와 기간 파라미터를 지정하여 조회한다")
        void getMyCallHistory_withParams_thenReturnsFilteredHistory() throws Exception {
            // given
            CallHistoryResponse.Pagination pagination = new CallHistoryResponse.Pagination(
                    2, 3, 50, true);
            CallHistoryResponse response = CallHistoryResponse.of(Collections.emptyList(), pagination);
            given(callHistoryService.getCallHistory(1L, 2, 10, "week")).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/users/me/call-history")
                            .param("page", "2")
                            .param("limit", "10")
                            .param("period", "week")
)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }
}
