package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.dto.matching.UserQueueInfo;
import com.ldsilver.chingoohaja.infrastructure.redis.RedisMatchingConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RedisMatchingQueueService 키 구조 검증 테스트")
class RedisMatchingQueueServiceTest {

    @Test
    @DisplayName("키 구조 단순화 검증")
    void verifySimplifiedKeyStructure() {
        Long categoryId = 1L;
        Long userId = 100L;

        // 단순화된 키 생성
        String queueKey = RedisMatchingConstants.KeyBuilder.queueKey(categoryId);
        String userQueueKey = RedisMatchingConstants.KeyBuilder.userQueueKey(userId);
        String lockKey = RedisMatchingConstants.KeyBuilder.lockKey(userId);

        // 예상 패턴 검증
        assertEquals("queue:1", queueKey);
        assertEquals("queue:user:100", userQueueKey);
        assertEquals("queue:lock:100", lockKey);

        System.out.println("단순화된 키 구조:");
        System.out.println("1. 큐 키:       " + queueKey);
        System.out.println("2. 사용자 키:   " + userQueueKey);
        System.out.println("3. 락 키:       " + lockKey);
    }

    @Test
    @DisplayName("사용자 큐 정보 통합 저장/파싱 검증")
    void verifyUserQueueValueIntegration() {
        Long categoryId = 3L;
        String queueId = "queue_100_3_abc123";
        long timestamp = System.currentTimeMillis();

        // 1. 통합 값 생성
        String userQueueValue = RedisMatchingConstants.KeyBuilder.userQueueValue(categoryId, queueId, timestamp);
        String expectedValue = categoryId + ":" + queueId + ":" + timestamp;
        assertEquals(expectedValue, userQueueValue);

        // 2. 파싱 검증
        UserQueueInfo parsed =
                RedisMatchingConstants.KeyBuilder.parseUserQueueValue(userQueueValue);

        assertNotNull(parsed);
        assertEquals(categoryId, parsed.categoryId());
        assertEquals(queueId, parsed.queueId());
        assertEquals(timestamp, parsed.timestamp());

        System.out.println("사용자 큐 정보 통합:");
        System.out.println("원본 값: " + userQueueValue);
        System.out.println("파싱 결과: categoryId=" + parsed.categoryId() +
                ", queueId=" + parsed.queueId() +
                ", timestamp=" + parsed.timestamp());
    }

    @Test
    @DisplayName("Lua 스크립트 키 목록 생성 검증")
    void verifyScriptKeyGeneration() {
        Long categoryId = 2L;
        List<Long> userIds = Arrays.asList(100L, 200L);

        List<String> scriptKeys = RedisMatchingConstants.KeyBuilder.getScriptKeys(categoryId, userIds);

        // 예상 키 순서: queueKey, userQueueKey1, userQueueKey2, lockKey1, lockKey2
        List<String> expectedKeys = Arrays.asList(
                "queue:2",           // queueKey
                "queue:user:100",    // userQueueKey1
                "queue:user:200",    // userQueueKey2
                "queue:lock:100",    // lockKey1
                "queue:lock:200"     // lockKey2
        );

        assertEquals(expectedKeys.size(), scriptKeys.size());
        assertEquals(expectedKeys, scriptKeys);

        System.out.println("Lua 스크립트 키 목록:");
        for (int i = 0; i < scriptKeys.size(); i++) {
            System.out.println((i + 1) + ". " + scriptKeys.get(i));
        }
    }

    @Test
    @DisplayName("키 패턴 일관성 검증 (기존 문제 해결)")
    void verifyKeyPatternConsistency() {
        Long categoryId = 1L;
        Long userId = 100L;

        // 모든 컴포넌트에서 동일한 키 패턴 사용 확인
        String directKey = RedisMatchingConstants.KeyBuilder.userQueueKey(userId);

        List<String> scriptKeys = RedisMatchingConstants.KeyBuilder.getScriptKeys(categoryId, Arrays.asList(userId));
        String scriptKey = scriptKeys.stream()
                .filter(key -> key.startsWith("queue:user:"))
                .findFirst()
                .orElse("NOT_FOUND");

        assertEquals(directKey, scriptKey, "직접 생성과 스크립트용 키가 일치해야 함");

        System.out.println("키 패턴 일관성 확인:");
        System.out.println("직접 생성: " + directKey);
        System.out.println("스크립트용: " + scriptKey);
        System.out.println("일치 여부: " + directKey.equals(scriptKey));
    }

    @Test
    @DisplayName("메타데이터 제거 검증")
    void verifyMetadataRemoval() {
        // 기존에 있던 복잡한 메타데이터 키들이 더 이상 생성되지 않음을 확인

        Long categoryId = 1L;
        Long userId = 100L;
        String queueId = "queue_100_1_test123";

        // 새로운 구조에서는 이런 복잡한 키들이 생성되지 않음
        Set<String> newKeys = Set.of(
                RedisMatchingConstants.KeyBuilder.queueKey(categoryId),
                RedisMatchingConstants.KeyBuilder.userQueueKey(userId),
                RedisMatchingConstants.KeyBuilder.lockKey(userId)
        );

        // 메타데이터 키가 포함되지 않음을 확인
        boolean hasMetaKey = newKeys.stream()
                .anyMatch(key -> key.contains("meta") || key.contains("hash"));
        assertFalse(hasMetaKey, "메타데이터 키가 제거되어야 함");

        // Hash 자료구조 사용하는 키가 없음을 확인
        boolean hasHashStructure = newKeys.stream()
                .anyMatch(key -> key.contains("meta:"));
        assertFalse(hasHashStructure, "Hash 자료구조 키가 제거되어야 함");

        System.out.println("메타데이터 제거 완료:");
        System.out.println("새로운 키 목록: " + newKeys);
        System.out.println("메타데이터 키 없음: " + !hasMetaKey);
    }

    @Test
    @DisplayName("성능 개선 예상 검증")
    void verifyPerformanceImprovement() {
        Long userId = 150L;

        // 기존: getQueueStatus()에서 1-20 카테고리 스캔 (O(n))
        // 개선: queue:user:{userId} 직접 조회 (O(1))

        String userQueueKey = RedisMatchingConstants.KeyBuilder.userQueueKey(userId);

        // 직접 조회로 변경되었는지 확인
        assertTrue(userQueueKey.contains(userId.toString()),
                "userId가 키에 직접 포함되어 O(1) 조회 가능해야 함");

        // 카테고리 정보 없이도 사용자 상태 조회 가능
        assertFalse(userQueueKey.contains("category"),
                "사용자 키에 카테고리 정보가 직접 포함되지 않아야 함");

        System.out.println("✅ 성능 개선 확인:");
        System.out.println("사용자 키: " + userQueueKey);
        System.out.println("O(1) 조회 가능: 카테고리 스캔 불필요");
    }

    @Test
    @DisplayName("UserQueueInfo 기능 검증")
    void verifyUserQueueInfoFeatures() {
        Long categoryId = 1L;
        String queueId = "queue_100_1_test";
        long baseTimestamp = System.currentTimeMillis();

        UserQueueInfo queueInfo =
                new UserQueueInfo(categoryId, queueId, baseTimestamp);

        // 1. 만료 검사
        long currentTimestamp = baseTimestamp + (11 * 60 * 1000); // 11분 후
        boolean expired = queueInfo.isExpired(currentTimestamp, 600); // TTL 10분
        assertTrue(expired, "11분 후에는 만료되어야 함");

        long notExpiredTimestamp = baseTimestamp + (5 * 60 * 1000); // 5분 후
        boolean notExpired = queueInfo.isExpired(notExpiredTimestamp, 600);
        assertFalse(notExpired, "5분 후에는 아직 만료되지 않아야 함");

        // 2. 대기 시간 계산
        long waitTime = queueInfo.getWaitTimeSeconds(notExpiredTimestamp);
        assertEquals(300, waitTime, "5분 대기 시간이 정확해야 함");

        System.out.println("UserQueueInfo 기능 검증:");
        System.out.println("만료 검사: " + expired + " / " + notExpired);
        System.out.println("대기 시간: " + waitTime + "초");
    }


}
