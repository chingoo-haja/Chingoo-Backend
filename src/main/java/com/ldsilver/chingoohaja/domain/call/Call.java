package com.ldsilver.chingoohaja.domain.call;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.call.enums.CallStatus;
import com.ldsilver.chingoohaja.domain.call.enums.CallType;
import com.ldsilver.chingoohaja.domain.category.Category;
import com.ldsilver.chingoohaja.domain.common.BaseEntity;
import com.ldsilver.chingoohaja.domain.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
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

    // ========== Agora 관련 필드 추가 ==========
    @Column(length = 64)
    @Size(max = 64, message = "Agora 채널명은 64자를 초과할 수 없습니다.")
    private String agoraChannelName;

    @Column(length = 100)
    @Size(max = 100, message = "Agora Resource ID는 100자를 초과할 수 없습니다.")
    private String agoraResourceId;

    @Column(length = 100)
    @Size(max = 100, message = "Agora SID는 100자를 초과할 수 없습니다.")
    private String agoraSid;

    @Column(length = 2048)
    @Size(max = 2048, message = "녹음 파일 URL은 2048자를 초과할 수 없습니다.")
    private String recordingFileUrl;

    private LocalDateTime recordingStartedAt;

    private LocalDateTime recordingEndedAt;

    private Integer recordingDurationSeconds;

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
            throw new CustomException(ErrorCode.CALL_START_FAILED, this.callStatus.name());
        }
    }

}
