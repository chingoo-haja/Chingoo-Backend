package com.ldsilver.chingoohaja.controller;

import com.ldsilver.chingoohaja.dto.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class HealthCheckController {
    private final DataSource dataSource;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("{application.version:1.0.0}")
    private String applicationVersion;

    @GetMapping
    public ApiResponse<Map<String, Object>> healthCheck() {
        Map<String, Object> healthInfo = new HashMap<>();
        boolean isHealthy = checkBasicHealth();
        healthInfo.put("status", isHealthy ? "UP" : "DOWN");
        healthInfo.put("timestamp", LocalDateTime.now());
        healthInfo.put("service", "chingoo-haja-backend");
        healthInfo.put("version", applicationVersion);

        return ApiResponse.ok("서비스가 정상적으로 동작 중입니다.", healthInfo);
    }

    @GetMapping("/detailed")
    public ApiResponse<Map<String, Object>> detailedHealthCheck() {
        Map<String, Object> healthInfo = new HashMap<>();
        healthInfo.put("timestamp", LocalDateTime.now());
        healthInfo.put("service", "chingoo-haja-backend");

        // 데이터베이스 연결 확인
        Map<String, Object> dbHealth = checkDatabaseHealth();
        healthInfo.put("database", dbHealth);

        // Redis 연결 확인
        Map<String, Object> redisHealth = checkRedisHealth();
        healthInfo.put("redis", redisHealth);

        // 전체 상태 결정
        Boolean dbHealthy = (Boolean) dbHealth.get("healthy");
        Boolean redisHealthy = (Boolean) redisHealth.get("healthy");
        boolean isHealthy = Boolean.TRUE.equals(dbHealthy) && Boolean.TRUE.equals(redisHealthy);        healthInfo.put("status", isHealthy ? "UP" : "DOWN");
        healthInfo.put("healthy", isHealthy);

        return ApiResponse.ok("상세 헬스체크 완료", healthInfo);
    }

    private boolean checkBasicHealth() {
        try{
            return dataSource.getConnection().isValid(1);
        } catch(Exception e) {
            return false;
        }
    }

    private Map<String, Object> checkDatabaseHealth() {
        Map<String, Object> dbHealth = new HashMap<>();

        try (Connection connection = dataSource.getConnection()) {
            boolean isValid = connection.isValid(5); // 5초 타임아웃
            dbHealth.put("status", isValid ? "UP" : "DOWN");
            dbHealth.put("healthy", isValid);
            dbHealth.put("url", connection.getMetaData().getURL());
            dbHealth.put("driver", connection.getMetaData().getDriverName());
        } catch (Exception e) {
            log.error("Database health check failed", e);
            dbHealth.put("status", "DOWN");
            dbHealth.put("healthy", false);
            dbHealth.put("error", e.getMessage());
        }

        return dbHealth;
    }

    private Map<String, Object> checkRedisHealth() {
        Map<String, Object> redisHealth = new HashMap<>();

        try {
            // Redis PING 테스트
            String result = Objects.requireNonNull(redisTemplate.getConnectionFactory())
                    .getConnection()
                    .ping();

            boolean isHealthy = "PONG".equals(result);
            redisHealth.put("status", isHealthy ? "UP" : "DOWN");
            redisHealth.put("healthy", isHealthy);
            redisHealth.put("ping", result);
        } catch (Exception e) {
            log.error("Redis health check failed", e);
            redisHealth.put("status", "DOWN");
            redisHealth.put("healthy", false);
            redisHealth.put("error", e.getMessage());
        }

        return redisHealth;
    }
}
