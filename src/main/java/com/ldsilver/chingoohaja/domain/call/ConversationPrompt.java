package com.ldsilver.chingoohaja.domain.call;

import com.ldsilver.chingoohaja.domain.category.Category;
import com.ldsilver.chingoohaja.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "conversation_prompts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConversationPrompt extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 500)
    private String question;

    @Column(nullable = false)
    private Integer difficulty; // 1: 쉬움, 2: 보통, 3: 깊은 질문

    @Column(nullable = false)
    private Boolean isActive = true;

    private Integer displayOrder; // 우선순위

    public static ConversationPrompt of(Category category, String question, Integer difficulty) {
        ConversationPrompt prompt = new ConversationPrompt();
        prompt.category = category;
        prompt.question = question;
        prompt.difficulty = difficulty;
        return prompt;
    }

    public void activate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public void updateQuestion(String newQuestion) {
        this.question = newQuestion;
    }

    public void updateDifficulty(Integer newDifficulty) {
        if (newDifficulty < 1 || newDifficulty > 3) {
            throw new IllegalArgumentException("난이도는 1-3 사이여야 합니다.");
        }
        this.difficulty = newDifficulty;
    }
}