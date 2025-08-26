package com.ldsilver.chingoohaja.domain.matching;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.category.Category;
import com.ldsilver.chingoohaja.domain.common.BaseEntity;
import com.ldsilver.chingoohaja.domain.matching.enums.QueueStatus;
import com.ldsilver.chingoohaja.domain.matching.enums.QueueType;
import com.ldsilver.chingoohaja.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "matching_queue")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchingQueue extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QueueType queueType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QueueStatus queueStatus;

    public static MatchingQueue of(
            User user,
            Category category,
            QueueType queueType,
            QueueStatus queueStatus
    ) {
        MatchingQueue matchingQueue = new MatchingQueue();
        matchingQueue.user = user;
        matchingQueue.category = category;
        matchingQueue.queueType = queueType;
        matchingQueue.queueStatus = queueStatus;
        return matchingQueue;
    }

    public static MatchingQueue from(User user, Category category, QueueType queueType) {
        return of(user, category, queueType, QueueStatus.WAITING);
    }

    public void cancel() {
        if (this.queueStatus == QueueStatus.WAITING) {
            this.queueStatus = QueueStatus.CANCELLED;
        } else {
            throw new CustomException(ErrorCode.STATUS_NOT_WAITING, this.queueStatus);
        }
    }


    public void startMatching() {
        if (this.queueStatus == QueueStatus.WAITING) {
            this.queueStatus = QueueStatus.MATCHING;
        } else {
            throw new CustomException(ErrorCode.START_MATCHING_FAILED, this.queueStatus);
        }
    }


    public void expire() {
        if (this.queueStatus == QueueStatus.WAITING) {
            this.queueStatus = QueueStatus.EXPIRED;
        } else {
            throw new CustomException(ErrorCode.MATCHING_EXPIRED_FAILED, this.queueStatus);
        }
    }

    public boolean isWaiting() {
        return this.queueStatus == QueueStatus.WAITING;
    }

    public boolean isMatching() {
        return this.queueStatus == QueueStatus.MATCHING;
    }

    public boolean isCancelled() {
        return this.queueStatus == QueueStatus.CANCELLED;
    }

    public boolean isExpired() {
        return this.queueStatus == QueueStatus.EXPIRED;
    }

    public boolean isActive() {
        return this.queueStatus == QueueStatus.WAITING || this.queueStatus == QueueStatus.MATCHING;
    }
}
