package com.ldsilver.chingoohaja.infrastructure.websocket;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.infrastructure.jwt.JwtTokenProvider;
import com.ldsilver.chingoohaja.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractTokenFromHeaders(accessor);

            if (token != null) {
                try {
                    Authentication authentication = authenticateToken(token);
                    accessor.setUser(authentication);
                    log.debug("WebSocket 연결 인증 성공 - userId: {}",
                            ((CustomUserDetails) authentication.getPrincipal()).getUserId());


                    log.debug("인증 설정 완료 - principal type: {}",
                            authentication.getPrincipal().getClass().getName());
                    log.debug("Principal is CustomUserDetails?: {}",
                            authentication.getPrincipal() instanceof CustomUserDetails);

                    if (authentication.getPrincipal() instanceof CustomUserDetails) {
                        CustomUserDetails details = (CustomUserDetails) authentication.getPrincipal();
                        log.debug("CustomUserDetails.user is null?: {}", details.getUser() == null);
                        if (details.getUser() != null) {
                            log.debug("User ID: {}", details.getUser().getId());
                        }
                    }


                } catch (CustomException ce) {
                    log.warn("WebSocket 인증 실패 - code: {}, message: {}", ce.getErrorCode().name(), ce.getMessage());
                    throw ce;
                } catch (Exception e) {
                    log.error("WebSocket 인증 실패: {}", e.getMessage());
                    throw new MessagingException("WebSocket authentication failed", e);
                }
            } else {
                log.error("WebSocket 연결 시 토큰이 없음");
                throw new RuntimeException("Authorization token required");
            }
        }
        return message;
    }

    private String extractTokenFromHeaders(StompHeaderAccessor accessor) {
        // Authorization 헤더에서 토큰 추출
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        return null;
    }

    private Authentication authenticateToken(String token) {
        try {
            if (!jwtTokenProvider.isTokenValid(token)) {
                throw new CustomException(ErrorCode.INVALID_TOKEN);
            }

            if (!jwtTokenProvider.isAccessToken(token)) {
                throw new CustomException(ErrorCode.INVALID_ACCESS_TOKEN);
            }

            Long userId = jwtTokenProvider.getUserIdFromToken(token);
            log.debug("토큰에서 사용자 ID 추출 - userId: {}", userId);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

            CustomUserDetails userDetails = new CustomUserDetails(user);
            return new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            );
        } catch (Exception e) {
            log.error("토큰 인증 실패: {}",e.getMessage());
            throw e;
        }
    }
}
