package com.ldsilver.chingoohaja.domain.evaluation;

import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.common.BaseEntity;
import com.ldsilver.chingoohaja.domain.evaluation.enums.FeedbackType;
import com.ldsilver.chingoohaja.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "evaluations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Evaluation extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "call_id", nullable = false)
    private Call call;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluator_id", nullable = false)
    private User evaluator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluated_id", nullable = false)
    private User evaluated;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", nullable = false)
    private FeedbackType feedbackType;

    public static Evaluation of(
            Call call,
            User evaluator,
            User evaluated,
            FeedbackType feedbackType
    ) {
        validateEvaluation(evaluator, evaluated);

        Evaluation evaluation = new Evaluation();
        evaluation.call = call;
        evaluation.evaluator = evaluator;
        evaluation.evaluated = evaluated;
        evaluation.feedbackType = feedbackType;
        return evaluation;
    }

    private static void validateEvaluation(User evaluator, User evaluated) {
        if (evaluator.equals(evaluated)) {
            throw new IllegalArgumentException("자기 자신을 평가할 수 없습니다.");
        }
    }
}
