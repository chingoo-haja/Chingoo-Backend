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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
}
