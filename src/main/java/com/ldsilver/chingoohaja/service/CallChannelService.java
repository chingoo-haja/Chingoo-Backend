package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.dto.call.CallChannelInfo;
import com.ldsilver.chingoohaja.dto.call.response.ChannelResponse;
import com.ldsilver.chingoohaja.repository.CallRepository;
import com.ldsilver.chingoohaja.validation.CallValidationConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
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

        CallChannelInfo channelInfo = getChannelInfo(channelName);

        if (channelInfo == null) {
            throw new CustomException(ErrorCode.CALL_NOT_FOUND, "채널을 찾을 수 없습니다: " + channelName);
        }

        if (channelInfo.isFull()) {
            throw new CustomException(ErrorCode.CALL_SESSION_ERROR, "채널이 가득 찼습니다: " + channelName);
        }

        if (channelInfo.isExpired()) {
            throw new CustomException(ErrorCode.CALL_SESSION_ERROR, "채널이 만료되었습니다: " + channelName);
        }

        String existingChannel = getUserCurrentChannel(userId);
        if (existingChannel != null && !existingChannel.equals(channelName)) {
            throw new CustomException(ErrorCode.SESSION_ALREADY_JOINED);
        }

        CallChannelInfo updatedChannelInfo = channelInfo.addParticipant(userId);
        storeChannelInfo(updatedChannelInfo);

        // 사용자-채널 매핑 저장
        setUserCurrentChannel(userId, channelName);

        log.info("채널 참가 완료 - channelName: {}, userId: {}, participants: {}/{}",
                channelName, userId, updatedChannelInfo.currentParticipants(), updatedChannelInfo.maxParticipants());

        return ChannelResponse.joined(updatedChannelInfo, userId);
    }

    @Transactional
    public ChannelResponse leaveChannel(String channelName, Long userId) {
        log.debug("채널 나가기 시작 - channelName: {}, userId: {}", channelName, userId);

        CallChannelInfo channelInfo = getChannelInfo(channelName);

        if (channelInfo == null) {
            log.warn("존재하지 않는 채널에서 나가기 시도 - channelName: {}, userId: {}", channelName, userId);
            return null;
        }

        if (!channelInfo.hasParticipant(userId)) {
            log.warn("참가하지 않은 채널에서 나가기 시도 - channelName: {}, userId: {}", channelName, userId);
            return ChannelResponse.from(channelInfo);
        }

        CallChannelInfo updatedChannelInfo = channelInfo.removeParticipant(userId);

        clearUserCurrentChannel(userId);

        if (updatedChannelInfo.isEmpty()) {
            deleteChannel(channelName);
            log.info("빈 채널 삭제 - channelName: {}", channelName);
            return null;
        } else {
            storeChannelInfo(updatedChannelInfo);
            log.info("채널 나가기 완료 - channelName: {}, userId: {}, participants: {}/{}",
                    channelName, userId, updatedChannelInfo.currentParticipants(), updatedChannelInfo.maxParticipants());
            return ChannelResponse.from(updatedChannelInfo);
        }
    }

    @Transactional
    public void deleteChannel(String channelName) {
        log.debug("채널 삭제 시작 - channelName: {}", channelName);

        CallChannelInfo channelInfo = getChannelInfo(channelName);

        if (channelInfo != null) {
            for (Long userId: channelInfo.participantIds()) {
                clearUserCurrentChannel(userId);
            }
        }

        String channelKey = CHANNEL_PREFIX + channelName;
        String participantsKey = CHANNEL_PARTICIPANTS_PREFIX + channelName;

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
            Set<String> channelKeys = redisTemplate.keys(CHANNEL_PREFIX + "*");

            if (channelKeys == null || channelKeys.isEmpty()) {
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
            log.error("채널 정리 스케줄러 실행 실패", e);
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
            Set<String> channelKeys = redisTemplate.keys(CHANNEL_PREFIX + "*");

            if (channelKeys == null || channelKeys.isEmpty()) {
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
            Set<String> channelKeys = redisTemplate.keys(CHANNEL_PREFIX + "*");

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






    private String generateChannelName(Call call) {
        long timestamp = System.currentTimeMillis();
        String randomSuffix = UUID.randomUUID().toString().substring(0,8);

        String channelName = String.format("call_%d_%d_%s",
                call.getId(), timestamp, randomSuffix);

        if (channelName.getBytes().length > CallValidationConstants.CHANNEL_NAME_MAX_BYTES) {
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
            channelData.put("createAt", channelInfo.createdAt().toString());
            channelData.put("expiresAt", channelInfo.expiresAt().toString());
            channelData.put("isActive", channelInfo.isActive());

            redisTemplate.opsForHash().putAll(channelKey, channelData);

            redisTemplate.delete(participantKey);
            if (!channelInfo.participantIds().isEmpty()) {
                redisTemplate.opsForSet().add(participantKey,
                        channelInfo.participantIds().toArray());
            }

            long ttlSeconds = Duration.between(
                    LocalDateTime.now(),
                    channelInfo.expiresAt().plusHours(1)
            ).getSeconds();

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
            redisTemplate.opsForValue().set(userChannelKey, channelName, 2, TimeUnit.HOURS);
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
