package com.ldsilver.chingoohaja.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:}")
    private List<String> allowedOrigins;

    @Bean("corsConfigurationSource")
    @Profile({"local", "dev"})
    public CorsConfigurationSource devCorsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:*",
                "https://localhost:*",
                "http://127.0.0.1:*",
                "null"
        ));

        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"
        ));

        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers"
        ));

        configuration.setExposedHeaders(Arrays.asList(
                "Authorization",
                "Set-Cookie"
        ));

        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;

    }

    @Bean("corsConfigurationSource")
    @Profile("prod")
    public CorsConfigurationSource prodCorsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        List<String> patterns = Arrays.asList(
                "https://chingoo-frontend.vercel.app",
                "https://www.chingoo-frontend.vercel.app",
                "https://chingoohaja.app",
                "https://www.chingoohaja.app",
                "https://api.chingoohaja.app",
                "https://silverld.site",
                "https://localhost",
                "http://localhost",
                "capacitor://localhost",
                "ionic://localhost"
        );

        configuration.setAllowedOriginPatterns(patterns);

        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"
        ));

        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers",
                "Cache-Control",
                "Pragma"
        ));

        configuration.setExposedHeaders(Arrays.asList(
                "Authorization",
                "Set-Cookie" // 쿠키 헤더 노출 추가
        ));

        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    // 기본/테스트 등(local/dev/prod가 아닌) 프로필용 fallback
    @Bean("corsConfigurationSource")
    @Profile("!local & !dev & !prod")
    public CorsConfigurationSource defaultCorsConfigurationSource() {
        return new UrlBasedCorsConfigurationSource(); // 등록된 매핑 없음 → CORS 비활성 동작
    }
}
