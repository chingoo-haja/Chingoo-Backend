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
    @DisplayName("키 패턴 일치 확인: 수정 후 3개 컴포넌트가 동일한 키 패턴 사용")
    void verifyCompleteKeyPatternMatch() {
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
        String luaExpectedKey = "user:queued:" + userId + ":";

        System.out.println("1. KeyBuilder 키:     " + keyBuilderKey);
        System.out.println("2. getCleanupKeys 키: " + cleanupKey);
        System.out.println("3. Lua 스크립트 예상: " + luaExpectedKey);

        // ✅ 수정 후: 모두 같은 패턴이어야 함
        Set<String> uniquePatterns = Set.of(keyBuilderKey, cleanupKey, luaExpectedKey);
        assertEquals(1, uniquePatterns.size(), "✅ 수정 후: 3개 모두 동일한 키 패턴을 사용해야 함!");

        // 개별 일치 검증
        assertEquals(keyBuilderKey, luaExpectedKey, "KeyBuilder와 Lua 스크립트 키 일치");
        assertEquals(cleanupKey, luaExpectedKey, "getCleanupKeys와 Lua 스크립트 키 일치");
        assertEquals(keyBuilderKey, cleanupKey, "KeyBuilder와 getCleanupKeys 키 일치");

        System.out.println("✅ 모든 컴포넌트가 동일한 키 패턴 사용: " + keyBuilderKey);
    }

    @Test
    @DisplayName("Hash Tag 일관성 검증")
    public void verifyRedisClusterHashTags() {
        Long categoryId = 1L;

        // 현재 관련 키들의 hash tag 확인
        String queueKey = RedisMatchingConstants.KeyBuilder.queueKey(categoryId);
        String waitQueueKey = RedisMatchingConstants.KeyBuilder.waitQueueKey(categoryId);

        List<String> cleanupKeys = RedisMatchingConstants.KeyBuilder.getCleanupKeys(categoryId, Arrays.asList(100L));

        System.out.println("Queue Key: " + queueKey);
        System.out.println("Wait Queue Key: " + waitQueueKey);
        System.out.println("Cleanup Keys: " + cleanupKeys);

        // Hash tag 추출 및 일관성 확인
        String queueHashTag = extractHashTag(queueKey);
        String waitQueueHashTag = extractHashTag(waitQueueKey);

        assertEquals(queueHashTag, waitQueueHashTag,
                "큐 관련 키들이 같은 hash tag를 사용해야 Redis Cluster에서 같은 슬롯에 위치");

        // user:queued 키는 hash tag가 없어서 다른 슬롯에 위치할 수 있음
        String userKey = cleanupKeys.get(2); // user:queued:100:
        String userHashTag = extractHashTag(userKey);

        if (userHashTag.isEmpty()) {
            System.err.println("⚠️ 경고: user:queued 키에 hash tag가 없어 다른 슬롯에 위치할 수 있음");
            System.err.println("Redis Cluster에서 Lua 스크립트 실행 시 CROSSSLOT 에러 발생 가능");
        }
    }

    private String extractHashTag(String key) {
        int start = key.indexOf('{');
        int end = key.indexOf('}');
        return (start >= 0 && end > start) ? key.substring(start + 1, end) : "";
    }
}
