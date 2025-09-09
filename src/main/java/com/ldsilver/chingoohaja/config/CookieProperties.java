package com.ldsilver.chingoohaja.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.cookie")
public class CookieProperties {
    private boolean secure = true;
    private String sameSite = "Lax";
    private int maxAge = 30 * 24 * 60 * 60; // 30일
}
