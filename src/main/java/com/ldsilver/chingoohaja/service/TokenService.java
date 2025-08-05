package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.config.JwtProperties;
import com.ldsilver.chingoohaja.infrastructure.jwt.JwtTokenProvider;
import com.ldsilver.chingoohaja.repository.UserRepository;
import com.ldsilver.chingoohaja.repository.UserTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    public TokenRespose generateTokens(Long userId, String deviceInfo) {

    }
}
