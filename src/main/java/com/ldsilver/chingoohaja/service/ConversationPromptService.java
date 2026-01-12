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
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

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
        Call call = callRepository.findByIdWithLock(callId)
                .orElseThrow(() -> new CustomException(ErrorCode.CALL_NOT_FOUND));

        if (!call.isParticipant(userId)) {
            throw new CustomException(ErrorCode.CALL_NOT_PARTICIPANT);
        }

        if (!call.isInProgress()) {
            throw new CustomException(ErrorCode.CALL_NOT_IN_PROGRESS);
        }

        // 2. DB에서 현재 표시중인 질문 확인
        Optional<PromptLog> currentPromptLog = promptLogRepository.findCurrentPromptByCallId(callId);

        if (currentPromptLog.isPresent()) {
            // 현재 표시 중인 질문이 있으면 그대로 반환
            ConversationPrompt prompt = currentPromptLog.get().getPrompt();
            log.info("기존 질문 반환 - callId: {}, userId: {}, promptId: {}",
                    callId, userId, prompt.getId());
            return PromptResponse.from(prompt);
        }

        // 3. 새로운 질문 선택
        ConversationPrompt selectedPrompt = selectNewPrompt(call, maxDifficulty);

        // 4. 로그 기록 (isCurrentlyDisplayed = true)
        PromptLog newLog = PromptLog.create(call, selectedPrompt);
        promptLogRepository.save(newLog);

        log.info("새 질문 제공 - callId: {}, userId: {}, promptId: {}, question: {}",
                callId, userId, selectedPrompt.getId(), selectedPrompt.getQuestion());

        return PromptResponse.from(selectedPrompt);
    }

    @Transactional
    public void moveToNextPrompt(Long callId, Long userId) {
        log.debug("다음 질문으로 이동 - callId: {}, userId: {}", callId, userId);

        Call call = callRepository.findByIdWithLock(callId)
                .orElseThrow(() -> new CustomException(ErrorCode.CALL_NOT_FOUND));

        if (!call.isParticipant(userId)) {
            throw new CustomException(ErrorCode.CALL_NOT_PARTICIPANT);
        }

        // ✅ 현재 표시 중인 질문의 isCurrentlyDisplayed를 false로 변경
        promptLogRepository.findCurrentPromptByCallId(callId)
                .ifPresent(promptLog -> {
                    promptLog.markAsDisplayed();
                    promptLogRepository.save(promptLog);
                });

        log.info("현재 질문 종료 처리 완료 - callId: {}, 다음 호출 시 새 질문 제공", callId);
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

        promptLogRepository.findLatestByCallIdAndPromptId(callId, promptId)
                .ifPresent(log -> {
                    log.markAsHelpful(helpful);
                    promptLogRepository.save(log);
                });

        log.info("질문 피드백 기록 완료 - callId: {}, promptId: {}, helpful: {}",
                callId, promptId, helpful);
    }

    private ConversationPrompt selectNewPrompt(Call call, Integer maxDifficulty) {
        // 이미 표시된 질문 ID 조회
        List<Long> displayedPromptIds = promptLogRepository.findDisplayedPromptIdsByCallId(call.getId());

        Long categoryId = call.getCategory() != null ? call.getCategory().getId() : null;
        List<ConversationPrompt> candidates;

        if (categoryId != null && maxDifficulty != null) {
            candidates = promptRepository.findRandomPromptsByCategory(categoryId, maxDifficulty);
        } else if (categoryId != null) {
            candidates = promptRepository.findByCategoryIdAndIsActiveTrueOrderByDisplayOrderAsc(categoryId);
        } else {
            candidates = promptRepository.findRandomActivePrompts();
        }

        List<ConversationPrompt> availablePrompts = candidates.stream()
                .filter(p -> !displayedPromptIds.contains(p.getId()))
                .toList();

        if (availablePrompts.isEmpty()) {
            log.warn("사용 가능한 질문이 없음 - callId: {}, 전체 질문으로 초기화", call.getId());
            availablePrompts = candidates;
        }

        if (availablePrompts.isEmpty()) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "해당 카테고리에 사용 가능한 질문이 없습니다.");
        }

        return availablePrompts.get(ThreadLocalRandom.current().nextInt(availablePrompts.size()));
    }
}