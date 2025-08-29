package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.infrastructure.redis.RedisMatchingConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379"
})
@DisplayName("RedisMatchingQueueService 키 패턴 검증 테스트")
public class RedisMatchingQueueServiceTest {

    @Test
    @DisplayName("3중 키 패턴 불일치 문제 확인")
    void verifyCompleteKeyPatternMismatch() {
        Long categoryId = 1L;
        Long userId = 100L;

        // 1) KeyBuilder가 생성하는 키
        String keyBuilderKey = RedisMatchingConstants.KeyBuilder.userQueueKey(userId);

        // 2) getCleanupKeys가 생성하는 키
        List<String> cleanupKeys = RedisMatchingConstants.KeyBuilder.getCleanupKeys(categoryId, Arrays.asList(userId));
        String cleanupKey = cleanupKeys.stream()
                .filter(key -> key.startsWith("user:queued:"))
                .findFirst()
                .orElse("NOT_FOUND");

        // 3) Lua 스크립트가 기대하는 키
        String luaExpectedKey = "user:queued:{cat:" + categoryId + "}:" + userId;

        System.out.println("1. KeyBuilder 키:     " + keyBuilderKey);
        System.out.println("2. getCleanupKeys 키: " + cleanupKey);
        System.out.println("3. Lua 스크립트 예상: " + luaExpectedKey);

        // 모두 다른 패턴인지 확인
        Set<String> uniquePatterns = Set.of(keyBuilderKey, cleanupKey, luaExpectedKey);
        assertEquals(3, uniquePatterns.size(), "3개 모두 다른 키 패턴을 사용하고 있음!");

        // 예상 결과:
        // 1. KeyBuilder 키:     user:queued:100:
        // 2. getCleanupKeys 키: user:queued:100:
        // 3. Lua 스크립트 예상: user:queued:{cat1}:100
    }
}
