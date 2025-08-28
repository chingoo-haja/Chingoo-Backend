package com.ldsilver.chingoohaja.domain.call;

import com.ldsilver.chingoohaja.domain.call.enums.CallStatus;
import com.ldsilver.chingoohaja.domain.call.enums.CallType;
import com.ldsilver.chingoohaja.domain.category.Category;
import com.ldsilver.chingoohaja.domain.common.BaseEntity;
import com.ldsilver.chingoohaja.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "calls")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Call extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user1_id", nullable = false)
    private User user1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user2_id", nullable = false)
    private User user2;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CallType callType;

    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CallStatus callStatus;

    public static Call of(
            User user1,
            User user2,
            Category category,
            CallType callType,
            CallStatus callStatus
    ) {
        validateUsers(user1, user2);

        Call call = new Call();
        call.user1 = user1;
        call.user2 = user2;
        call.category = category;
        call.callType = callType;
        call.callStatus = callStatus;
        return call;
    }

    public static Call from(User user1, User user2, Category category, CallType callType) {
        return of(user1, user2, category, callType, CallStatus.READY);
    }

    private static void validateUsers(User user1, User user2) {
        if (user1.equals(user2)) {
            throw new IllegalArgumentException("통화 참가자는 서로 다른 사용자여야 합니다.");
        }
    }

    public void startCall() {
        if (this.callStatus == CallStatus.READY) {
            this.callStatus = CallStatus.IN_PROGRESS;
            this.startAt = LocalDateTime.now();
        } else {
            throw new IllegalStateException("통화를 시작할 수 없는 상태입니다: " + this.callStatus);
        }
    }

}
