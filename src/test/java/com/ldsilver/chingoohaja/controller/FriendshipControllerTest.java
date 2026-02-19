package com.ldsilver.chingoohaja.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.dto.friendship.request.FriendRequestSendRequest;
import com.ldsilver.chingoohaja.dto.friendship.response.FriendListResponse;
import com.ldsilver.chingoohaja.dto.friendship.response.PendingFriendRequestListResponse;
import com.ldsilver.chingoohaja.dto.friendship.response.SentFriendRequestListResponse;
import com.ldsilver.chingoohaja.infrastructure.jwt.JwtAuthenticationFilter;
import com.ldsilver.chingoohaja.service.FriendshipService;
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
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FriendshipController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("FriendshipController 테스트")
class FriendshipControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FriendshipService friendshipService;

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
    @DisplayName("GET /api/v1/friendships - 친구 목록 조회")
    class GetFriendsList {

        @Test
        @DisplayName("친구 목록을 조회한다")
        void getFriendsList_thenReturnsFriendList() throws Exception {
            // given
            List<FriendListResponse.FriendItem> friends = List.of(
                    new FriendListResponse.FriendItem(2L, "친구1",
                            LocalDateTime.of(2025, 6, 1, 12, 0), "일상"),
                    new FriendListResponse.FriendItem(3L, "친구2",
                            LocalDateTime.of(2025, 5, 30, 10, 0), "고민상담")
            );
            FriendListResponse response = FriendListResponse.of(friends);
            given(friendshipService.getFriendsList(1L)).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/friendships"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.friends.length()").value(2))
                    .andExpect(jsonPath("$.data.total_count").value(2))
                    .andExpect(jsonPath("$.data.friends[0].nickname").value("친구1"));
        }

        @Test
        @DisplayName("친구가 없는 경우 빈 목록을 반환한다")
        void getFriendsList_whenEmpty_thenReturnsEmptyList() throws Exception {
            // given
            FriendListResponse response = FriendListResponse.of(Collections.emptyList());
            given(friendshipService.getFriendsList(1L)).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/friendships"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.friends").isArray())
                    .andExpect(jsonPath("$.data.total_count").value(0));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/friendships - 친구 요청 전송")
    class SendFriendRequest {

        @Test
        @DisplayName("닉네임으로 친구 요청을 전송한다")
        void sendFriendRequest_thenSuccess() throws Exception {
            // given
            FriendRequestSendRequest request = FriendRequestSendRequest.of("친구닉네임");
            willDoNothing().given(friendshipService).sendFriendRequest(eq(1L), anyString());

            // when & then
            mockMvc.perform(post("/api/v1/friendships")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("친구 요청을 전송했습니다."));
        }

        @Test
        @DisplayName("닉네임이 빈 값이면 400 에러를 반환한다")
        void sendFriendRequest_whenEmptyNickname_thenReturnsBadRequest() throws Exception {
            // given
            String invalidRequest = "{\"nickname\": \"\"}";

            // when & then
            mockMvc.perform(post("/api/v1/friendships")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidRequest))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/friendships/{friendshipId}/accept - 친구 요청 수락")
    class AcceptFriendRequest {

        @Test
        @DisplayName("친구 요청을 수락한다")
        void acceptFriendRequest_thenSuccess() throws Exception {
            // given
            willDoNothing().given(friendshipService).acceptFriendRequest(1L, 10L);

            // when & then
            mockMvc.perform(put("/api/v1/friendships/10/accept"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("친구 요청을 수락했습니다."));
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/friendships/{friendshipId}/reject - 친구 요청 거절")
    class RejectFriendRequest {

        @Test
        @DisplayName("친구 요청을 거절한다")
        void rejectFriendRequest_thenSuccess() throws Exception {
            // given
            willDoNothing().given(friendshipService).rejectFriendRequest(1L, 10L);

            // when & then
            mockMvc.perform(put("/api/v1/friendships/10/reject"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("친구 요청을 거절했습니다."));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/friendships/requests/received - 받은 친구 요청 목록 조회")
    class GetPendingFriendRequests {

        @Test
        @DisplayName("받은 친구 요청 목록을 조회한다")
        void getPendingFriendRequests_thenReturnsRequests() throws Exception {
            // given
            List<PendingFriendRequestListResponse.PendingFriendRequestItem> items = List.of(
                    new PendingFriendRequestListResponse.PendingFriendRequestItem(
                            10L, 2L, "요청자1", null,
                            LocalDateTime.of(2025, 6, 1, 12, 0))
            );
            PendingFriendRequestListResponse response = PendingFriendRequestListResponse.of(items);
            given(friendshipService.getPendingFriendRequests(1L)).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/friendships/requests/received"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.requests.length()").value(1))
                    .andExpect(jsonPath("$.data.total_count").value(1));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/friendships/requests/sent - 보낸 친구 요청 목록 조회")
    class GetSentFriendRequests {

        @Test
        @DisplayName("보낸 친구 요청 목록을 조회한다")
        void getSentFriendRequests_thenReturnsRequests() throws Exception {
            // given
            List<SentFriendRequestListResponse.SentFriendRequestItem> items = List.of(
                    new SentFriendRequestListResponse.SentFriendRequestItem(
                            11L, 3L, "대상자1", null,
                            LocalDateTime.of(2025, 6, 1, 12, 0))
            );
            SentFriendRequestListResponse response = SentFriendRequestListResponse.of(items);
            given(friendshipService.getSentFriendRequests(1L)).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/friendships/requests/sent"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.requests.length()").value(1))
                    .andExpect(jsonPath("$.data.total_count").value(1));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/friendships/requests/{friendshipId} - 보낸 친구 요청 취소")
    class CancelSentFriendRequest {

        @Test
        @DisplayName("보낸 친구 요청을 취소한다")
        void cancelSentFriendRequest_thenSuccess() throws Exception {
            // given
            willDoNothing().given(friendshipService).cancelSentFriendRequest(1L, 10L);

            // when & then
            mockMvc.perform(delete("/api/v1/friendships/requests/10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("친구 요청을 취소했습니다."));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/friendships/{friendId} - 친구 삭제")
    class DeleteFriendship {

        @Test
        @DisplayName("친구를 삭제한다")
        void deleteFriendship_thenSuccess() throws Exception {
            // given
            willDoNothing().given(friendshipService).deleteFriendship(1L, 5L);

            // when & then
            mockMvc.perform(delete("/api/v1/friendships/5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("친구를 삭제했습니다."));
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/friendships/{friendshipId}/block - 사용자 차단")
    class BlockUser {

        @Test
        @DisplayName("사용자를 차단한다")
        void blockUser_thenSuccess() throws Exception {
            // given
            willDoNothing().given(friendshipService).blockUser(1L, 10L);

            // when & then
            mockMvc.perform(put("/api/v1/friendships/10/block"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("사용자를 차단했습니다."));
        }
    }
}
