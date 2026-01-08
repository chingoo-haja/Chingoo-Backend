package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.ConversationPrompt;
import com.ldsilver.chingoohaja.domain.call.PromptLog;
import com.ldsilver.chingoohaja.dto.call.response.PromptResponse;
import com.ldsilver.chingoohaja.repository.CallRepository;
import com.ldsilver.chingoohaja.repository.ConversationPromptRepository;
import com.ldsilver.chingoohaja.repository.PromptLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationPromptService {

    private final ConversationPromptRepository promptRepository;
    private final PromptLogRepository promptLogRepository;
    private final CallRepository callRepository;
    private final Random random = new Random();

    /**
     * 통화 중 랜덤 질문 제공
     */
    @Transactional
    public PromptResponse getRandomPrompt(Long callId, Long userId, Integer maxDifficulty) {
        log.debug("질문 조회 - callId: {}, userId: {}, maxDifficulty: {}",
                callId, userId, maxDifficulty);

        // 1. Call 조회 및 권한 검증
        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new CustomException(ErrorCode.CALL_NOT_FOUND));

        if (!call.isParticipant(userId)) {
            throw new CustomException(ErrorCode.CALL_NOT_PARTICIPANT);
        }

        // 2. 통화 상태 확인
        if (!call.isInProgress()) {
            throw new CustomException(ErrorCode.CALL_NOT_IN_PROGRESS);
        }

        // 3. 이미 표시된 질문 ID 조회
        List<Long> displayedPromptIds = promptLogRepository.findDisplayedPromptIdsByCallId(callId);

        // 4. Call의 Category 기반 질문 조회
        Long categoryId = call.getCategory() != null ? call.getCategory().getId() : null;
        List<ConversationPrompt> candidates;

        if (categoryId != null && maxDifficulty != null) {
            // 특정 카테고리 + 난이도 필터링
            candidates = promptRepository.findRandomPromptsByCategory(categoryId, maxDifficulty);
        } else if (categoryId != null) {
            // 특정 카테고리만 (난이도 제한 없음)
            candidates = promptRepository.findByCategoryIdAndIsActiveTrueOrderByDisplayOrderAsc(categoryId);
        } else {
            // 카테고리 없음 - 전체 랜덤
            candidates = promptRepository.findRandomActivePrompts();
        }

        // 5. 이미 표시된 질문 제외
        List<ConversationPrompt> availablePrompts = candidates.stream()
                .filter(p -> !displayedPromptIds.contains(p.getId()))
                .toList();

        // 6. 랜덤 선택
        if (availablePrompts.isEmpty()) {
            log.warn("사용 가능한 질문이 없음 - callId: {}, categoryId: {}, 표시된 질문 초기화",
                    callId, categoryId);
            // 모든 질문을 다 사용한 경우, 전체 중에서 랜덤 선택
            availablePrompts = candidates;
        }

        if (availablePrompts.isEmpty()) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "해당 카테고리에 사용 가능한 질문이 없습니다.");
        }

        ConversationPrompt selectedPrompt = availablePrompts.get(
                random.nextInt(availablePrompts.size())
        );

        // 7. 로그 기록
        PromptLog promptLog = PromptLog.create(call, selectedPrompt);
        promptLogRepository.save(promptLog);

        log.info("질문 제공 완료 - callId: {}, categoryId: {}, promptId: {}, question: {}",
                callId, categoryId, selectedPrompt.getId(), selectedPrompt.getQuestion());

        return PromptResponse.from(selectedPrompt);
    }

    /**
     * 질문 피드백 기록
     */
    @Transactional
    public void recordPromptFeedback(Long callId, Long promptId, Long userId, boolean helpful) {
        log.debug("질문 피드백 기록 - callId: {}, promptId: {}, helpful: {}",
                callId, promptId, helpful);

        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new CustomException(ErrorCode.CALL_NOT_FOUND));

        if (!call.isParticipant(userId)) {
            throw new CustomException(ErrorCode.CALL_NOT_PARTICIPANT);
        }

        List<PromptLog> logs = promptLogRepository.findByCallIdOrderByDisplayedAtDesc(callId);

        logs.stream()
                .filter(l -> l.getPrompt().getId().equals(promptId))
                .findFirst()
                .ifPresent(log -> {
                    log.markAsHelpful(helpful);
                    promptLogRepository.save(log);
                });

        log.info("질문 피드백 기록 완료 - callId: {}, promptId: {}, helpful: {}",
                callId, promptId, helpful);
    }
}