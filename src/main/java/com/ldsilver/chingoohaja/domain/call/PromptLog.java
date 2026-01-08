package com.ldsilver.chingoohaja.domain.call;

import com.ldsilver.chingoohaja.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "prompt_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PromptLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "call_id", nullable = false)
    private Call call;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prompt_id", nullable = false)
    private ConversationPrompt prompt;

    @Column(nullable = false)
    private LocalDateTime displayedAt;

    // 나중에 질문 효과 분석용
    private Boolean wasHelpful; // 사용자 피드백

    public static PromptLog create(Call call, ConversationPrompt prompt) {
        PromptLog log = new PromptLog();
        log.call = call;
        log.prompt = prompt;
        log.displayedAt = LocalDateTime.now();
        return log;
    }

    public void markAsHelpful(boolean helpful) {
        this.wasHelpful = helpful;
    }
}