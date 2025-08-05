package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.config.JwtProperties;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.domain.user.UserToken;
import com.ldsilver.chingoohaja.dto.auth.response.TokenResponse;
import com.ldsilver.chingoohaja.infrastructure.jwt.JwtTokenProvider;
import com.ldsilver.chingoohaja.repository.UserRepository;
import com.ldsilver.chingoohaja.repository.UserTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenCacheService tokenCacheService;
    private final UserRepository userRepository;
    private final UserTokenRepository userTokenRepository;
    private final JwtProperties jwtProperties;

    private static final int MAX_TOKENS_PER_USER = 5;

    @Transactional
    public TokenResponse generateTokens(Long userId, String deviceInfo) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getUserType().name()
        );

        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        LocalDateTime refreshTokenExpiration = LocalDateTime.now()
                .plusSeconds(jwtProperties.getRefreshTokenExpiration() / 1000);

        UserToken userToken = UserToken.of(
                user,
                refreshToken,
                refreshTokenExpiration,
                deviceInfo,
                true
        );

        userTokenRepository.save(userToken);

        Duration cacheDuration = Duration.ofMillis(jwtProperties.getRefreshTokenExpiration());
        tokenCacheService.storeRefreshToken(userId, refreshToken, cacheDuration);

        limitUserTokens(user);

        log.debug("새로운 토큰 쌍 생성 완료 - userId: {}, deviceInfo: {}", userId, deviceInfo);

        return TokenResponse.of(accessToken, refreshToken, jwtProperties.getAccessTokenExpiration());
    }

    private void limitUserTokens(User user) {
        List<UserToken> activeTokens = userTokenRepository
                .findActiveTokensByUserOrderByCreatedAtDesc(user);
        if (activeTokens.size() > MAX_TOKENS_PER_USER) {
            List<UserToken> tokensToDeactivate = activeTokens.subList(MAX_TOKENS_PER_USER, activeTokens.size());

            for (UserToken token : tokensToDeactivate) {
                userTokenRepository.deactivateTokenByRefreshToken(token.getRefreshToken());
                tokenCacheService.deleteRefreshToken(token.getRefreshToken());
            }

            log.debug("사용자 토큰 개수 제한 - userId: {}, 제거된 토큰 수: {}", user.getId(), tokensToDeactivate.size());
        }
    }
}
