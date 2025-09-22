package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.dto.call.CallChannelInfo;
import com.ldsilver.chingoohaja.dto.call.response.ChannelResponse;
import com.ldsilver.chingoohaja.infrastructure.redis.RedisMatchingConstants;
import com.ldsilver.chingoohaja.repository.CallRepository;
import com.ldsilver.chingoohaja.validation.CallValidationConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallChannelService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CallRepository callRepository;

    private static final String CHANNEL_PREFIX = "call:channel:";
    private static final String CHANNEL_PARTICIPANTS_PREFIX = "call:participants:";
    private static final String USER_CHANNEL_PREFIX = "call:user_channel:";
    private static final long USER_CHANNEL_TTL_SECONDS = TimeUnit.HOURS.toSeconds(2);

    @Transactional
    public ChannelResponse createChannel(Call call) {
        log.debug("채널 생성 시작 - callId: {}", call.getId());

        String channelName = generateChannelName(call);

        call.setAgoraChannelInfo(channelName);
        callRepository.save(call);

        CallChannelInfo channelInfo = CallChannelInfo.empty(channelName, call.getId());
        storeChannelInfo(channelInfo);

        log.info("채널 생성 완료 - channelName: {}, callId: {}", channelName, call.getId());
        return ChannelResponse.created(channelInfo);
    }

    @Transactional
    public ChannelResponse joinChannel(String channelName, Long userId) {
        log.debug("채널 참가 시작 - channelName: {}, userId: {}", channelName, userId);

        // call 권한 검증
        Call call = callRepository.findByAgoraChannelName(channelName)
                .orElseThrow(() -> new CustomException(ErrorCode.CALL_NOT_FOUND));

        if (!call.isParticipant(userId)) {
            throw new CustomException(ErrorCode.CALL_NOT_PARTICIPANT);
        }

        String channelKey = CHANNEL_PREFIX + channelName;
        String participantsKey = CHANNEL_PARTICIPANTS_PREFIX + channelName;
        String userChannelKey = USER_CHANNEL_PREFIX + userId;

        // 현재 시간을 초 단위로 변환 (Redis에서 비교용)
        long currentTimeSeconds = System.currentTimeMillis() / 1000;

        // Lua 스크립트 실행
        RedisScript<Long> script = RedisScript.of(RedisMatchingConstants.LuaScripts.JOIN_CHANNEL_LUA_SCRIPT, Long.class);
        Long result = redisTemplate.execute(script,
                Arrays.asList(channelKey, participantsKey, userChannelKey),
                userId.toString(), channelName, String.valueOf(USER_CHANNEL_TTL_SECONDS), String.valueOf(currentTimeSeconds));

        // 결과에 따른 예외 처리
        if (result == null || result < 0) {
            switch (result == null ? -1 : result.intValue()) {
                case -1 -> throw new CustomException(ErrorCode.CALL_NOT_FOUND, "채널을 찾을 수 없습니다: " + channelName);
                case -2 -> throw new CustomException(ErrorCode.CALL_SESSION_ERROR, "이미 다른 채널에 참가 중입니다");
                case -3 -> throw new CustomException(ErrorCode.CALL_SESSION_ERROR, "비활성화된 채널입니다: " + channelName);
                case -4 -> throw new CustomException(ErrorCode.CALL_SESSION_ERROR, "채널이 만료되었습니다: " + channelName);
                case -5 -> throw new CustomException(ErrorCode.CALL_SESSION_ERROR, "채널이 가득 찼습니다: " + channelName);
                default -> throw new CustomException(ErrorCode.CALL_SESSION_ERROR, "채널 참가 실패");
            }
        }

        // 업데이트된 채널 정보 조회 및 반환
        CallChannelInfo updatedChannelInfo = getChannelInfo(channelName);
        log.info("채널 참가 완료 - channelName: {}, userId: {}, 현재 참가자 수: {}",
                channelName, userId, result);

        return ChannelResponse.joined(updatedChannelInfo, userId);
    }

    @Transactional
    public ChannelResponse leaveChannel(String channelName, Long userId) {
        log.debug("채널 나가기 시작 - channelName: {}, userId: {}", channelName, userId);

        CallChannelInfo channelInfo = getChannelInfo(channelName);

        if (channelInfo == null) {
            log.warn("존재하지 않는 채널에서 나가기 시도 - channelName: {}, userId: {}", channelName, userId);
            throw new CustomException(ErrorCode.CALL_NOT_FOUND);
        }

        if (!channelInfo.hasParticipant(userId)) {
            log.warn("참가하지 않은 채널에서 나가기 시도 - channelName: {}, userId: {}", channelName, userId);
            return ChannelResponse.from(channelInfo);
        }

        // 원자적 제거
        String participantsKey = CHANNEL_PARTICIPANTS_PREFIX + channelName;
        try {
                redisTemplate.opsForSet().remove(participantsKey, String.valueOf(userId));
            } finally {
                clearUserCurrentChannel(userId);
            }

        // 빈 채널이면 삭제, 아니면 최신 스냅샷 반환
        Long size = redisTemplate.opsForSet().size(participantsKey);
        if (size == null || size == 0) {
                deleteChannel(channelName);
                log.info("빈 채널 삭제 - channelName: {}", channelName);
                return null;
        }
        CallChannelInfo latest = getChannelInfo(channelName);
        log.info("채널 나가기 완료 - channelName: {}, userId: {}, participants: {}",
                        channelName, userId, size);
        return ChannelResponse.from(latest);
    }

    @Transactional
    public void deleteChannel(String channelName) {
        log.debug("채널 삭제 시작 - channelName: {}", channelName);

        CallChannelInfo channelInfo = getChannelInfo(channelName);
        String participantsKey = CHANNEL_PARTICIPANTS_PREFIX + channelName;
        if (channelInfo != null) {
                for (Long userId: channelInfo.participantIds()) clearUserCurrentChannel(userId);
            } else {
                java.util.Set<Object> orphanMembers = redisTemplate.opsForSet().members(participantsKey);
                if (orphanMembers != null) {
                        for (Object obj : orphanMembers) {
                                clearUserCurrentChannel(Long.valueOf(obj.toString()));
                            }
                    }
            }

        String channelKey = CHANNEL_PREFIX + channelName;

        redisTemplate.delete(channelKey);
        redisTemplate.delete(participantsKey);

        log.info("채널 삭제 완료 - channelName: {}", channelName);
    }

    @Transactional(readOnly = true)
    public ChannelResponse getChannelStatus(String channelName) {
        log.debug("채널 상태 조회 - channelName: {}", channelName);

        CallChannelInfo channelInfo = getChannelInfo(channelName);

        if (channelInfo == null) {
            throw new CustomException(ErrorCode.CALL_NOT_FOUND);
        }
        return ChannelResponse.status(channelInfo);
    }

    @Transactional(readOnly = true)
    public String getUserCurrentChannelName(Long userId) {
        return getUserCurrentChannel(userId);
    }

    /**
     * 만료된 채널 정리 (스케줄러)
     */
    @Scheduled(fixedDelay = 300000) // 5분마다 실행
    @Transactional
    public void cleanupExpiredChannels() {
        log.debug("만료된 채널 정리 시작");

        try {
            Set<String> channelKeys = scanKeysWithPattern(CHANNEL_PREFIX + "*");

            if (channelKeys.isEmpty()) {
                return;
            }

            int cleanedCount = 0;

            for (String channelKey : channelKeys) {
                try {
                    String channelName = channelKey.substring(CHANNEL_PREFIX.length());
                    CallChannelInfo channelInfo = getChannelInfo(channelName);

                    if (channelInfo != null && (channelInfo.isExpired() || !channelInfo.isActive())) {
                        deleteChannel(channelName);
                        cleanedCount++;
                        log.debug("만료된 채널 정리 - channelName: {}", channelName);
                    }
                } catch (Exception e) {
                    log.warn("채널 정리 중 오류 발생 - channelKey: {}", channelKey, e);
                }
            }

            if (cleanedCount > 0) {
                log.info("만료된 채널 정리 완료 - 정리된 채널 수: {}", cleanedCount);
            }

        } catch (Exception e) {
            log.error("만료된 채널 정리 스케줄러 실행 실패", e);
        }
    }

    /**
     * 빈 채널 정리 (스케줄러)
     */
    @Scheduled(fixedDelay = 180000) // 3분마다 실행
    @Transactional
    public void cleanupEmptyChannels() {
        log.debug("빈 채널 정리 시작");

        try {
            Set<String> channelKeys = scanKeysWithPattern(CHANNEL_PREFIX + "*");

            if (channelKeys.isEmpty()) {
                return;
            }

            int cleanedCount = 0;

            for (String channelKey : channelKeys) {
                try {
                    String channelName = channelKey.substring(CHANNEL_PREFIX.length());
                    CallChannelInfo channelInfo = getChannelInfo(channelName);

                    if (channelInfo != null && channelInfo.isEmpty()) {
                        deleteChannel(channelName);
                        cleanedCount++;
                        log.debug("빈 채널 정리 - channelName: {}", channelName);
                    }
                } catch (Exception e) {
                    log.warn("빈 채널 정리 중 오류 발생 - channelKey: {}", channelKey, e);
                }
            }

            if (cleanedCount > 0) {
                log.info("빈 채널 정리 완료 - 정리된 채널 수: {}", cleanedCount);
            } else {
                log.info("빈 채널 정리 완료  - 정리된 채널이 없습니다.");
            }

        } catch (Exception e) {
            log.error("빈 채널 정리 스케줄러 실행 실패", e);
        }
    }

    /**
     * 모든 활성 채널 조회 (관리자용)
     */
    @Transactional(readOnly = true)
    public List<ChannelResponse> getAllActiveChannels() {
        log.debug("모든 활성 채널 조회");

        try {
            Set<String> channelKeys = scanKeysWithPattern(CHANNEL_PREFIX + "*");

            if (channelKeys == null || channelKeys.isEmpty()) {
                return Collections.emptyList();
            }

            List<ChannelResponse> activeChannels = new ArrayList<>();

            for (String channelKey : channelKeys) {
                try {
                    String channelName = channelKey.substring(CHANNEL_PREFIX.length());
                    CallChannelInfo channelInfo = getChannelInfo(channelName);

                    if (channelInfo != null && channelInfo.isActive() && !channelInfo.isExpired()) {
                        activeChannels.add(ChannelResponse.from(channelInfo));
                    }
                } catch (Exception e) {
                    log.warn("채널 정보 조회 중 오류 발생 - channelKey: {}", channelKey, e);
                }
            }

            log.debug("활성 채널 조회 완료 - 채널 수: {}", activeChannels.size());
            return activeChannels;

        } catch (Exception e) {
            log.error("활성 채널 조회 실패", e);
            return Collections.emptyList();
        }
    }




    /**
     * Redis KEYS 대신 SCAN을 사용하여 패턴 매칭 키 조회 (논블로킹)
     */
    private Set<String> scanKeysWithPattern(String pattern) {
        Set<String> keys = new HashSet<>();

        redisTemplate.execute((RedisCallback<Void>) connection -> {
            try (Cursor<byte[]> cursor = connection.scan(
                    ScanOptions.scanOptions()
                            .match(pattern)
                            .count(1000)
                            .build())) {

                while (cursor.hasNext()) {
                    byte[] key = cursor.next();
                    String keyStr = (String) redisTemplate.getKeySerializer().deserialize(key);
                    if (keyStr != null) {
                        keys.add(keyStr);
                    }
                }
            } catch (Exception e) {
                log.error("Redis SCAN 중 오류 발생: {}", e.getMessage(), e);
            }
            return null;
        });

        return keys;
    }

    private String generateChannelName(Call call) {
        long timestamp = System.currentTimeMillis();
        String randomSuffix = UUID.randomUUID().toString().substring(0,8);

        String channelName = String.format("call_%d_%d_%s",
                call.getId(), timestamp, randomSuffix);

        if (channelName.getBytes(StandardCharsets.UTF_8).length > CallValidationConstants.CHANNEL_NAME_MAX_BYTES) {
            channelName = String.format("c_%d_%s", call.getId(), randomSuffix);
        }
        return channelName;
    }

    private void storeChannelInfo(CallChannelInfo channelInfo) {
        try {
            String channelKey = CHANNEL_PREFIX + channelInfo.channelName();
            String participantKey = CHANNEL_PARTICIPANTS_PREFIX + channelInfo.channelName();

            Map<String, Object> channelData = new HashMap<>();
            channelData.put("channelName", channelInfo.channelName());
            channelData.put("callId", channelInfo.callId());
            channelData.put("maxParticipants", channelInfo.maxParticipants());
            channelData.put("createdAt", channelInfo.createdAt().toString());
            channelData.put("expiresAt", channelInfo.expiresAt().toString());
            channelData.put("expiresAtEpoch", String.valueOf(channelInfo.expiresAt().toEpochSecond(ZoneOffset.UTC)));
            channelData.put("isActive", channelInfo.isActive());

            redisTemplate.opsForHash().putAll(channelKey, channelData);

            redisTemplate.delete(participantKey);
            if (!channelInfo.participantIds().isEmpty()) {
                final byte[] keyBytes = participantKey.getBytes(StandardCharsets.UTF_8);
                redisTemplate.execute((RedisCallback<Long>) conn -> {
                        List<byte[]> members = new ArrayList<>(channelInfo.participantIds().size());
                        for (Long id : channelInfo.participantIds()) {
                                members.add(String.valueOf(id).getBytes(StandardCharsets.UTF_8));
                            }
                        return conn.sAdd(keyBytes, members.toArray(new byte[0][]));
                    });
            }

            long ttlSeconds = Duration.between(
                    LocalDateTime.now(),
                    channelInfo.expiresAt().plusHours(1)
            ).getSeconds();
            if (ttlSeconds <= 0) {
                ttlSeconds = TimeUnit.HOURS.toSeconds(1);
            }

            redisTemplate.expire(channelKey, ttlSeconds, TimeUnit.SECONDS);
            redisTemplate.expire(participantKey, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("채널 정보 저장 실패 - channelName: {}", channelInfo.channelName(), e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public CallChannelInfo getChannelInfo(String channelName) {
        try {
            String channelKey = CHANNEL_PREFIX + channelName;
            Map<Object, Object> channelData = redisTemplate.opsForHash().entries(channelKey);

            if (channelData.isEmpty()) {
                return null;
            }

            String participantsKey = CHANNEL_PARTICIPANTS_PREFIX + channelName;
            Set<Object> participantObjects = redisTemplate.opsForSet().members(participantsKey);

            Set<Long> participantIds = new HashSet<>();
            if (participantObjects != null) {
                for (Object obj : participantObjects) {
                    participantIds.add(Long.valueOf(obj.toString()));
                }
            }

            return new CallChannelInfo(
                    (String) channelData.get("channelName"),
                    Long.valueOf(channelData.get("callId").toString()),
                    Integer.parseInt(channelData.get("maxParticipants").toString()),
                    participantIds.size(),
                    participantIds,
                    LocalDateTime.parse(channelData.get("createdAt").toString()),
                    LocalDateTime.parse(channelData.get("expiresAt").toString()),
                    Boolean.parseBoolean(channelData.get("isActive").toString())
            );
        } catch (Exception e) {
            log.error("채널 정보 조회 실패 - channelName: {}", channelName, e);
            return null;
        }
    }

    private String getUserCurrentChannel(Long userId) {
        try {
            String userChannelKey = USER_CHANNEL_PREFIX + userId;
            Object channel = redisTemplate.opsForValue().get(userChannelKey);
            return channel != null ? channel.toString() : null;
        } catch (Exception e) {
            log.error("사용자 채널 조회 실패 - userId: {}", userId, e);
            return null;
        }
    }

    private void setUserCurrentChannel(Long userId, String channelName) {
        try {
            String userChannelKey = USER_CHANNEL_PREFIX + userId;
            redisTemplate.opsForValue().set(userChannelKey, channelName, USER_CHANNEL_TTL_SECONDS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("사용자 채널 설정 실패 - userId: {}, channelName: {}", userId, channelName, e);
        }
    }

    private void clearUserCurrentChannel(Long userId) {
        try {
            String userChannelKey = USER_CHANNEL_PREFIX + userId;
            redisTemplate.delete(userChannelKey);
        } catch (Exception e) {
            log.error("사용자 채널 정보 삭제 실패 - userId: {}", userId, e);
        }
    }
}
