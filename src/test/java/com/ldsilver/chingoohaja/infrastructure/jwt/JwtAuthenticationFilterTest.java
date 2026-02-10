package com.ldsilver.chingoohaja.infrastructure.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.domain.user.enums.Gender;
import com.ldsilver.chingoohaja.domain.user.enums.UserType;
import com.ldsilver.chingoohaja.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter 테스트")
class JwtAuthenticationFilterTest {

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private UserRepository userRepository;
    @Mock private FilterChain filterChain;

    @InjectMocks private JwtAuthenticationFilter jwtAuthenticationFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private User testUser;

    private static final String VALID_TOKEN = "valid.jwt.token";

    @BeforeEach
    void setUp() {
        // ObjectMapper를 직접 주입 (mock 대신 실제 인스턴스)
        try {
            Field objectMapperField = JwtAuthenticationFilter.class.getDeclaredField("objectMapper");
            objectMapperField.setAccessible(true);
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            objectMapperField.set(jwtAuthenticationFilter, objectMapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();

        testUser = User.of("test@test.com", "테스터", "테스터닉", Gender.MALE,
                LocalDate.of(1990, 1, 1), null, UserType.USER, null, "kakao", "k1");
        setId(testUser, 1L);
    }

    private void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("doFilterInternal")
    class DoFilterInternal {

        @Test
        @DisplayName("유효한 액세스 토큰이면 SecurityContext에 인증 정보를 설정한다")
        void givenValidAccessToken_whenFilter_thenSetsAuthentication() throws ServletException, IOException {
            // given
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
            when(jwtTokenProvider.isTokenValid(VALID_TOKEN)).thenReturn(true);
            when(jwtTokenProvider.isAccessToken(VALID_TOKEN)).thenReturn(true);
            when(jwtTokenProvider.getUserIdFromToken(VALID_TOKEN)).thenReturn(1L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

            // when
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Authorization 헤더가 없으면 인증 없이 필터 체인을 진행한다")
        void givenNoAuthHeader_whenFilter_thenContinuesWithoutAuth() throws ServletException, IOException {
            // when
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("유효하지 않은 토큰이면 인증 없이 필터 체인을 진행한다")
        void givenInvalidToken_whenFilter_thenContinuesWithoutAuth() throws ServletException, IOException {
            // given
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
            when(jwtTokenProvider.isTokenValid(VALID_TOKEN)).thenReturn(false);

            // when
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("액세스 토큰이 아니면 인증 없이 필터 체인을 진행한다")
        void givenNonAccessToken_whenFilter_thenContinuesWithoutAuth() throws ServletException, IOException {
            // given
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
            when(jwtTokenProvider.isTokenValid(VALID_TOKEN)).thenReturn(true);
            when(jwtTokenProvider.isAccessToken(VALID_TOKEN)).thenReturn(false);

            // when
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("WebSocket 경로는 인증 필터를 건너뛴다")
        void givenWebSocketPath_whenFilter_thenSkipsAuthentication() throws ServletException, IOException {
            // given
            request.setRequestURI("/ws/matching");
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);

            // when
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // then
            verify(jwtTokenProvider, never()).isTokenValid(anyString());
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("만료된 JWT 토큰이면 401 에러 응답을 반환한다")
        void givenExpiredToken_whenFilter_thenReturns401() throws ServletException, IOException {
            // given
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
            when(jwtTokenProvider.isTokenValid(VALID_TOKEN))
                    .thenThrow(new io.jsonwebtoken.ExpiredJwtException(null, null, "Token expired"));

            // when
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentType()).startsWith("application/json");
            verify(filterChain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("사용자를 찾을 수 없으면 401 에러 응답을 반환한다")
        void givenUserNotFound_whenFilter_thenReturns401() throws ServletException, IOException {
            // given
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
            when(jwtTokenProvider.isTokenValid(VALID_TOKEN)).thenReturn(true);
            when(jwtTokenProvider.isAccessToken(VALID_TOKEN)).thenReturn(true);
            when(jwtTokenProvider.getUserIdFromToken(VALID_TOKEN)).thenReturn(999L);
            when(userRepository.findById(999L))
                    .thenThrow(new CustomException(ErrorCode.USER_NOT_FOUND));

            // when
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(response.getStatus()).isEqualTo(401);
            verify(filterChain, never()).doFilter(request, response);
        }
    }
}
