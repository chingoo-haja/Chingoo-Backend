package com.ldsilver.chingoohaja.listener;

import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.repository.CallRepository;
import com.ldsilver.chingoohaja.service.CallGracePeriodService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final CallRepository callRepository;
    private final CallGracePeriodService gracePeriodService;

    /**
     * WebSocket 연결 시 - 유예 기간 취소
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = headerAccessor.getUser();

        if (principal != null) {
            Long userId = Long.parseLong(principal.getName());
            log.info("WebSocket 연결 - userId: {}", userId);

            // 활성 통화가 있으면 유예 기간 취소
            List<Call> activeCalls = callRepository.findActiveCallsByUserId(userId);
            if (!activeCalls.isEmpty()) {
                Call call = activeCalls.get(0);
                gracePeriodService.cancelGracePeriod(call.getId(), userId);
                log.info("유예 기간 취소 (재연결) - callId: {}, userId: {}", call.getId(), userId);
            }
        }
    }

    /**
     * WebSocket 연결 끊김 시 - 유예 기간 시작
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = headerAccessor.getUser();

        if (principal != null) {
            Long userId = Long.parseLong(principal.getName());
            log.info("WebSocket 연결 끊김 - userId: {}, sessionId: {}", userId, event.getSessionId());

            // 현재 활성 통화 확인
            List<Call> activeCalls = callRepository.findActiveCallsByUserId(userId);

            if (!activeCalls.isEmpty()) {
                Call call = activeCalls.get(0);

                // 유예 기간 시작 (30초)
                gracePeriodService.markUserDisconnected(call.getId(), userId);

                log.warn("통화 중 연결 끊김 - callId: {}, userId: {}, 30초 유예 시작",
                        call.getId(), userId);
            }
        }
    }
}
