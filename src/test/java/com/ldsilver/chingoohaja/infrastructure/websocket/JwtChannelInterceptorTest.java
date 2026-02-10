package com.ldsilver.chingoohaja.infrastructure.websocket;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.domain.user.enums.Gender;
import com.ldsilver.chingoohaja.domain.user.enums.UserType;
import com.ldsilver.chingoohaja.infrastructure.jwt.JwtTokenProvider;
import com.ldsilver.chingoohaja.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtChannelInterceptor 테스트")
class JwtChannelInterceptorTest {

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private UserRepository userRepository;
    @Mock private MessageChannel messageChannel;

    @InjectMocks private JwtChannelInterceptor jwtChannelInterceptor;

    private User testUser;
    private static final String VALID_TOKEN = "valid.jwt.token";

    @BeforeEach
    void setUp() {
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

    private Message<?> createConnectMessage(String token) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        if (token != null) {
            accessor.addNativeHeader("Authorization", "Bearer " + token);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<?> createSendMessage() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/test");
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Nested
    @DisplayName("preSend - CONNECT")
    class PreSendConnect {

        @Test
        @DisplayName("유효한 토큰으로 CONNECT 시 인증 정보를 설정한다")
        void givenValidToken_whenConnect_thenSetsAuthentication() {
            // given
            Message<?> message = createConnectMessage(VALID_TOKEN);
            when(jwtTokenProvider.isTokenValid(VALID_TOKEN)).thenReturn(true);
            when(jwtTokenProvider.isAccessToken(VALID_TOKEN)).thenReturn(true);
            when(jwtTokenProvider.getUserIdFromToken(VALID_TOKEN)).thenReturn(1L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

            // when
            Message<?> result = jwtChannelInterceptor.preSend(message, messageChannel);

            // then
            assertThat(result).isNotNull();
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
            assertThat(accessor.getUser()).isNotNull();
            assertThat(accessor.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);

            UsernamePasswordAuthenticationToken auth =
                    (UsernamePasswordAuthenticationToken) accessor.getUser();
            assertThat(auth.getPrincipal()).isInstanceOf(CustomUserDetails.class);
        }

        @Test
        @DisplayName("토큰이 없으면 RuntimeException을 던진다")
        void givenNoToken_whenConnect_thenThrowsException() {
            // given
            Message<?> message = createConnectMessage(null);

            // when & then
            assertThatThrownBy(() -> jwtChannelInterceptor.preSend(message, messageChannel))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Authorization token required");
        }

        @Test
        @DisplayName("유효하지 않은 토큰이면 CustomException을 던진다")
        void givenInvalidToken_whenConnect_thenThrowsCustomException() {
            // given
            Message<?> message = createConnectMessage(VALID_TOKEN);
            when(jwtTokenProvider.isTokenValid(VALID_TOKEN)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> jwtChannelInterceptor.preSend(message, messageChannel))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_TOKEN));
        }

        @Test
        @DisplayName("액세스 토큰이 아니면 CustomException을 던진다")
        void givenNonAccessToken_whenConnect_thenThrowsCustomException() {
            // given
            Message<?> message = createConnectMessage(VALID_TOKEN);
            when(jwtTokenProvider.isTokenValid(VALID_TOKEN)).thenReturn(true);
            when(jwtTokenProvider.isAccessToken(VALID_TOKEN)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> jwtChannelInterceptor.preSend(message, messageChannel))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_ACCESS_TOKEN));
        }

        @Test
        @DisplayName("사용자를 찾을 수 없으면 CustomException을 던진다")
        void givenUserNotFound_whenConnect_thenThrowsCustomException() {
            // given
            Message<?> message = createConnectMessage(VALID_TOKEN);
            when(jwtTokenProvider.isTokenValid(VALID_TOKEN)).thenReturn(true);
            when(jwtTokenProvider.isAccessToken(VALID_TOKEN)).thenReturn(true);
            when(jwtTokenProvider.getUserIdFromToken(VALID_TOKEN)).thenReturn(999L);
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> jwtChannelInterceptor.preSend(message, messageChannel))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("preSend - non-CONNECT")
    class PreSendNonConnect {

        @Test
        @DisplayName("CONNECT가 아닌 STOMP 커맨드는 인증 없이 통과시킨다")
        void givenSendCommand_whenPreSend_thenPassesThrough() {
            // given
            Message<?> message = createSendMessage();

            // when
            Message<?> result = jwtChannelInterceptor.preSend(message, messageChannel);

            // then
            assertThat(result).isNotNull();
            verify(jwtTokenProvider, never()).isTokenValid(anyString());
        }
    }
}
