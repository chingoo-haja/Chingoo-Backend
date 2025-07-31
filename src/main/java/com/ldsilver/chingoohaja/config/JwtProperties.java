package com.ldsilver.chingoohaja.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {
    private String secret;
    /**
     * Access Token 만료 시간 (밀리초)
     * 기본값: 1시간 (3600000ms)
     */
    private long accessTokenExpiration = 3600000L;

    /**
     * Refresh Token 만료 시간 (밀리초)
     * 기본값: 30일 (2592000000ms)
     */
    private long refreshTokenExpiration = 2592000000L;
    /**
     * JWT 발급자 정보
     */
    private String issuer = "chingoo-haja";

    /**
     * Access Token의 subject prefix
     */
    private String accessTokenSubject = "access_token";

    /**
     * Refresh Token의 subject prefix
     */
    private String refreshTokenSubject = "refresh_token";
}
