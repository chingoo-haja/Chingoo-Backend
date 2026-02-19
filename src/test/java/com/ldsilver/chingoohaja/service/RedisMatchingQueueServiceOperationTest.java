package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.infrastructure.redis.RedisMatchingConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisMatchingQueueService 동작 테스트")
class RedisMatchingQueueServiceOperationTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private ZSetOperations<String, String> zSetOperations;
    @Mock private SetOperations<String, String> setOperations;

    @InjectMocks private RedisMatchingQueueService redisMatchingQueueService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(redisMatchingQueueService, "blockedUsersTtl", 86400L);
    }

    @Nested
    @DisplayName("enqueueUser - 매칭 대기열 참가")
    class EnqueueUser {

        @Test
        @DisplayName("큐 참가 Lua 스크립트가 성공 응답을 반환하면 EnqueueResult(success=true)를 반환한다")
        void givenLuaScriptSucceeds_whenEnqueue_thenReturnsSuccess() {
            // given
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(List.of("1", "SUCCESS", "3"));

            // when
            RedisMatchingQueueService.EnqueueResult result =
                    redisMatchingQueueService.enqueueUser(100L, 1L, "queue_100_1_abc");

            // then
            assertThat(result.success()).isTrue();
            assertThat(result.position()).isEqualTo(3);
        }

        @Test
        @DisplayName("이미 큐에 있는 사용자는 EnqueueResult(success=false)를 반환한다")
        void givenAlreadyInQueue_whenEnqueue_thenReturnsFailure() {
            // given
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(List.of("0", "ALREADY_IN_QUEUE"));

            // when
            RedisMatchingQueueService.EnqueueResult result =
                    redisMatchingQueueService.enqueueUser(100L, 1L, "queue_100_1_abc");

            // then
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("ALREADY_IN_QUEUE");
        }

        @Test
        @DisplayName("Redis 오류 발생 시 EnqueueResult(success=false)를 반환한다")
        void givenRedisException_whenEnqueue_thenReturnsFailure() {
            // given
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenThrow(new RuntimeException("Redis 연결 실패"));

            // when
            RedisMatchingQueueService.EnqueueResult result =
                    redisMatchingQueueService.enqueueUser(100L, 1L, "queue_100_1_abc");

            // then
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo(RedisMatchingConstants.ResponseMessage.REDIS_ERROR);
        }
    }

    @Nested
    @DisplayName("dequeueUser - 매칭 대기열 탈퇴")
    class DequeueUser {

        @Test
        @DisplayName("큐 탈퇴 Lua 스크립트가 성공 응답을 반환하면 DequeueResult(success=true)를 반환한다")
        void givenLuaScriptSucceeds_whenDequeue_thenReturnsSuccess() {
            // given
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(List.of("1", "SUCCESS"));

            // when
            RedisMatchingQueueService.DequeueResult result =
                    redisMatchingQueueService.dequeueUser(100L, 1L);

            // then
            assertThat(result.success()).isTrue();
        }

        @Test
        @DisplayName("큐에 없는 사용자 탈퇴 시 DequeueResult(success=false)를 반환한다")
        void givenNotInQueue_whenDequeue_thenReturnsFailure() {
            // given
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(List.of("0", "NOT_IN_QUEUE"));

            // when
            RedisMatchingQueueService.DequeueResult result =
                    redisMatchingQueueService.dequeueUser(100L, 1L);

            // then
            assertThat(result.success()).isFalse();
        }
    }

    @Nested
    @DisplayName("getQueueStatus - 큐 상태 조회")
    class GetQueueStatus {

        @Test
        @DisplayName("큐에 없는 사용자 조회 시 null을 반환한다")
        void givenUserNotInQueue_whenGetStatus_thenReturnsNull() {
            // given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);

            // when
            RedisMatchingQueueService.QueueStatusInfo result =
                    redisMatchingQueueService.getQueueStatus(100L);

            // then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("큐 정보가 잘못된 형식이면 null을 반환한다")
        void givenMalformedQueueValue_whenGetStatus_thenReturnsNull() {
            // given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn("invalid-format");
            when(redisTemplate.delete(anyString())).thenReturn(true);

            // when
            RedisMatchingQueueService.QueueStatusInfo result =
                    redisMatchingQueueService.getQueueStatus(100L);

            // then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("유효한 큐 정보가 있으면 QueueStatusInfo를 반환한다")
        void givenValidQueueInfo_whenGetStatus_thenReturnsStatusInfo() {
            // given
            long timestamp = System.currentTimeMillis();
            String queueValue = RedisMatchingConstants.KeyBuilder.userQueueValue(1L, "queue_100_1_abc", timestamp);

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(queueValue);
            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.rank(anyString(), anyString())).thenReturn(2L);
            when(zSetOperations.zCard(anyString())).thenReturn(5L);

            // when
            RedisMatchingQueueService.QueueStatusInfo result =
                    redisMatchingQueueService.getQueueStatus(100L);

            // then
            assertThat(result).isNotNull();
            assertThat(result.position()).isEqualTo(3); // rank 2 + 1
            assertThat(result.totalWaiting()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("getWaitingCount - 대기 인원 수 조회")
    class GetWaitingCount {

        @Test
        @DisplayName("ZSet의 카디널리티를 반환한다")
        void givenUsersInQueue_whenGetCount_thenReturnsCount() {
            // given
            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.zCard(anyString())).thenReturn(3L);

            // when
            long count = redisMatchingQueueService.getWaitingCount(1L);

            // then
            assertThat(count).isEqualTo(3L);
        }

        @Test
        @DisplayName("Redis 오류 발생 시 0을 반환한다")
        void givenRedisException_whenGetCount_thenReturnsZero() {
            // given
            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.zCard(anyString())).thenThrow(new RuntimeException("Redis 오류"));

            // when
            long count = redisMatchingQueueService.getWaitingCount(1L);

            // then
            assertThat(count).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("saveBlockedUsers / isBlocked - 차단 사용자 관리")
    class BlockedUsers {

        @Test
        @DisplayName("차단 목록에 추가 후 isBlocked가 true를 반환한다")
        void givenBlockedUser_whenIsBlocked_thenReturnsTrue() {
            // given
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(redisTemplate.expire(anyString(), any())).thenReturn(true);
            when(setOperations.isMember("user:blocked:1", "2")).thenReturn(true);
            when(setOperations.isMember("user:blocked:2", "1")).thenReturn(false);

            // when
            redisMatchingQueueService.saveBlockedUsers(1L, List.of(2L));
            boolean blocked = redisMatchingQueueService.isBlocked(1L, 2L);

            // then
            assertThat(blocked).isTrue();
        }

        @Test
        @DisplayName("양방향 차단 여부를 확인한다")
        void givenBidirectionalBlock_whenIsBlocked_thenReturnsTrue() {
            // given
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(setOperations.isMember("user:blocked:1", "2")).thenReturn(false);
            when(setOperations.isMember("user:blocked:2", "1")).thenReturn(true);

            // when
            boolean blocked = redisMatchingQueueService.isBlocked(1L, 2L);

            // then
            assertThat(blocked).isTrue();
        }

        @Test
        @DisplayName("차단 관계가 없으면 isBlocked가 false를 반환한다")
        void givenNoBlock_whenIsBlocked_thenReturnsFalse() {
            // given
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(setOperations.isMember("user:blocked:1", "2")).thenReturn(false);
            when(setOperations.isMember("user:blocked:2", "1")).thenReturn(false);

            // when
            boolean blocked = redisMatchingQueueService.isBlocked(1L, 2L);

            // then
            assertThat(blocked).isFalse();
        }
    }

    @Nested
    @DisplayName("removeMatchedUsers - 매칭된 사용자 제거")
    class RemoveMatchedUsers {

        @Test
        @DisplayName("Lua 스크립트가 제거 수를 반환하면 RemoveUserResult(success=true)를 반환한다")
        void givenLuaScriptSucceeds_whenRemove_thenReturnsSuccess() {
            // given
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(2L);

            // when
            RedisMatchingQueueService.RemoveUserResult result =
                    redisMatchingQueueService.removeMatchedUsers(1L, List.of(100L, 200L));

            // then
            assertThat(result.success()).isTrue();
            assertThat(result.removedCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("제거된 수가 0이면 RemoveUserResult(success=false)를 반환한다")
        void givenNothingRemoved_whenRemove_thenReturnsFailure() {
            // given
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(0L);

            // when
            RedisMatchingQueueService.RemoveUserResult result =
                    redisMatchingQueueService.removeMatchedUsers(1L, List.of(100L, 200L));

            // then
            assertThat(result.success()).isFalse();
            assertThat(result.removedCount()).isEqualTo(0);
        }
    }
}
