package com.ldsilver.chingoohaja.controller;

import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.dto.matching.response.MatchingStatusResponse;
import com.ldsilver.chingoohaja.service.MatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final MatchingService matchingService;

    /**
     * 매칭 상태 구독 요청
     */
    @MessageMapping("/matching/subscribe")
    @SendToUser("/queue/matching")
    public MatchingStatusResponse subscribeMatchingStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.debug("매칭 상태 구독 요청 - userId: {}", userDetails.getUserId());

        try {
            return matchingService.getMatchingStatus(userDetails.getUserId());
        } catch (Exception e) {
            log.error("매칭 상태 구독 처리 실패 - userId: {}", userDetails.getUserId());
            return MatchingStatusResponse.notInQueue();
        }
    }
}
