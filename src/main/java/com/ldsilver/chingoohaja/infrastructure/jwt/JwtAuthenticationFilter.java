package com.ldsilver.chingoohaja.infrastructure.jwt;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try{
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
                    log.warn("JWT 토큰 만료: {}", e.getMessage());
                    SecurityContextHolder.clearContext();
                    response.setHeader("X-Token-Expired", "true");
                    response.setHeader("X-Token-Error", "TOKEN_EXPIRED");
                } catch (io.jsonwebtoken.MalformedJwtException e) {
                    log.warn("잘못된 형식의 JWT 토큰: {}", e.getMessage());
                    SecurityContextHolder.clearContext();

                } catch (io.jsonwebtoken.security.SignatureException e) {
                    log.warn("JWT 서명 검증 실패: {}", e.getMessage());
                    SecurityContextHolder.clearContext();

                } catch (CustomException e) {
                    log.debug("JWT 인증 실패: {}", e.getMessage());
                    SecurityContextHolder.clearContext();

                } catch (Exception e) {
                    log.error("JWT 인증 중 예외 발생", e);
                    SecurityContextHolder.clearContext();
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
}
