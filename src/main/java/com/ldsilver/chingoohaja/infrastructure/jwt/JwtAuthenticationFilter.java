package com.ldsilver.chingoohaja.infrastructure.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.dto.common.ErrorResponse;
import com.ldsilver.chingoohaja.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try{
            String requestURI = request.getRequestURI();
            if (requestURI.startsWith("/ws")) {
                log.debug("WebSocket 요청 - 인증 필터 건너뜀: {}", requestURI);
                filterChain.doFilter(request, response);
                return;
            }

            String token = extractTokenFromRequest(request);

            if (token != null) {
                try {
                    if (!jwtTokenProvider.isTokenValid(token)) {
                        log.debug("유효하지 않은 JWT 토큰");
                        SecurityContextHolder.clearContext();
                        filterChain.doFilter(request, response);
                        return;
                    }
                    if (!jwtTokenProvider.isAccessToken(token)) {
                        log.debug("Access Token이 아닙니다. {}", token);
                        SecurityContextHolder.clearContext();
                        filterChain.doFilter(request, response);
                        return;
                    }

                    Long userId = jwtTokenProvider.getUserIdFromToken(token);

                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

                    CustomUserDetails userDetails = new CustomUserDetails(user);
                    UsernamePasswordAuthenticationToken authenticationToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    authenticationToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                    log.debug("JWT 인증 성공 - 사용자: {}, 권한: {}", user.getEmail(), userDetails.getAuthorities());
                } catch (io.jsonwebtoken.ExpiredJwtException e) {
                    log.warn("JWT 토큰 만료 - URI: {}, {}", requestURI, e.getMessage());
                    SecurityContextHolder.clearContext();

                    sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                            ErrorCode.JWT_EXPIRED);
                    return;
                } catch (io.jsonwebtoken.MalformedJwtException e) {
                    log.warn("잘못된 형식의 JWT 토큰: {}", e.getMessage());
                    SecurityContextHolder.clearContext();

                    sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                            ErrorCode.INVALID_TOKEN);
                    return;
                } catch (io.jsonwebtoken.security.SignatureException e) {
                    log.warn("JWT 서명 검증 실패: {}", e.getMessage());
                    SecurityContextHolder.clearContext();

                    sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                            ErrorCode.INVALID_TOKEN);
                    return;
                } catch (CustomException e) {
                    log.debug("JWT 인증 실패: {}", e.getMessage());
                    SecurityContextHolder.clearContext();

                    sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                            e.getErrorCode());
                    return;
                } catch (Exception e) {
                    log.error("JWT 인증 중 예외 발생", e);
                    SecurityContextHolder.clearContext();

                    sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                            ErrorCode.INTERNAL_SERVER_ERROR);
                    return;
                }
            }
        } catch (Exception e) {
            log.debug("JWT 필터에서 예상치 못한 오류 발생", e);
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }


    private void sendErrorResponse(HttpServletResponse response, int status, ErrorCode errorCode)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse errorResponse = ErrorResponse.of(errorCode);

        String jsonResponse = objectMapper.writeValueAsString(errorResponse);
        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
    }
}
