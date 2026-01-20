package com.ldsilver.chingoohaja.scheduler;

import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.enums.CallStatus;
import com.ldsilver.chingoohaja.repository.CallRepository;
import com.ldsilver.chingoohaja.service.CallGracePeriodService;
import com.ldsilver.chingoohaja.service.CallStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GracePeriodScheduler {

    private final CallRepository callRepository;
    private final CallGracePeriodService gracePeriodService;
    private final CallStatusService callStatusService;

    /**
     * 10초마다 유예 기간이 끝난 통화 확인
     * Redis TTL이 만료되면 자동으로 키가 삭제되므로,
     * 유예 기간이 끝난 사용자를 감지하여 통화 종료 처리
     */
    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void checkExpiredGracePeriods() {
        List<Call> activeCalls = callRepository.findByCallStatus(CallStatus.IN_PROGRESS);

        if (activeCalls.isEmpty()) {
            return;
        }

        log.debug("유예 기간 체크 - 활성 통화 수: {}", activeCalls.size());

        for (Call call : activeCalls) {
            try {
                Long user1Id = call.getUser1().getId();
                Long user2Id = call.getUser2().getId();

                boolean user1InGrace = gracePeriodService.isInGracePeriod(call.getId(), user1Id);
                boolean user2InGrace = gracePeriodService.isInGracePeriod(call.getId(), user2Id);

                // 둘 다 유예 기간이 아님 = 둘 다 연결됨 또는 둘 다 30초 지남
                if (!user1InGrace && !user2InGrace) {
                    // 정상 상태 - 아무 작업 안 함
                    continue;
                }

                // 한쪽만 유예 기간 = 한 명은 연결 끊김, 다른 한 명은 연결됨
                if (user1InGrace != user2InGrace) {
                    log.debug("한쪽만 연결 끊김 - callId: {}, user1: {}, user2: {}",
                            call.getId(), user1InGrace, user2InGrace);
                    continue;
                }

                // 둘 다 유예 기간 중 = 둘 다 연결 끊김 (아직 30초 안 지남)
                log.debug("양쪽 모두 유예 기간 중 - callId: {}", call.getId());

            } catch (Exception e) {
                log.error("유예 기간 체크 실패 - callId: {}", call.getId(), e);
            }
        }
    }
}
