package com.ldsilver.chingoohaja.domain.call;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.call.enums.CallStatus;
import com.ldsilver.chingoohaja.domain.call.enums.CallType;
import com.ldsilver.chingoohaja.domain.category.Category;
import com.ldsilver.chingoohaja.domain.common.BaseEntity;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.validation.CallValidationConstants;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static com.ldsilver.chingoohaja.validation.CallValidationConstants.CHANNEL_NAME_PATTERN;

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
            throw new CustomException(ErrorCode.CALL_USER_NOT_EQUAL);
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

    // ========== Agora 관련 메서드 ==========

    public void setAgoraChannelInfo(String channelName) {
        validateChannelName(channelName);
        this.agoraChannelName = channelName;
    }

    public void startCloudRecording(String resourceId, String sid) {
        validateRecordingParams(resourceId, sid);

        if (this.callStatus != CallStatus.IN_PROGRESS) {
            throw new CustomException(ErrorCode.CALL_NOT_IN_PROGRESS);
        }

        this.agoraResourceId = resourceId;
        this.agoraSid = sid;
        this.recordingStartedAt = LocalDateTime.now();
    }

    public void stopCloudRecording(String recordingFileUrl) {
        if (this.recordingStartedAt == null) {
            throw new CustomException(ErrorCode.RECORDING_NOT_STARTED);
        }

        this.recordingFileUrl = recordingFileUrl;
        this.recordingEndedAt = LocalDateTime.now();

        if (this.recordingStartedAt != null) {
            this.recordingDurationSeconds = (int) java.time.Duration.between(
                    this.recordingStartedAt,
                    this.recordingEndedAt
            ).getSeconds();
        }
    }

    public void endCall() {
        if (this.callStatus == CallStatus.IN_PROGRESS) {
            this.callStatus = CallStatus.COMPLETED;
            this.endAt = LocalDateTime.now();

            if (this.startAt != null) {
                this.durationSeconds = (int) java.time.Duration.between(
                        this.startAt,
                        this.endAt
                ).getSeconds();
            }
        } else {
            throw new CustomException(ErrorCode.CALL_NOT_IN_PROGRESS);
        }
    }

    public void cancelCall() {
        if (this.callStatus == CallStatus.READY || this.callStatus == CallStatus.IN_PROGRESS) {
            this.callStatus = CallStatus.CANCELLED;
            this.endAt = LocalDateTime.now();
        } else {
            throw new CustomException(ErrorCode.CALL_ALREADY_ENDED);
        }
    }

    public void failCall() {
        this.callStatus = CallStatus.FAILED;
        this.endAt = LocalDateTime.now();
    }

    // ========== 검증 메서드 ==========

    private void validateChannelName(String channelName) {
        if (channelName == null || channelName.trim().isEmpty()) {
            throw new CustomException(ErrorCode.CHANNEL_NAME_REQUIRED);
        }
        if (channelName.length() > CallValidationConstants.CHANNEL_NAME_MAX_BYTES) {
            throw new CustomException(ErrorCode.CHANNEL_NAME_TOO_LONG);
        }
        if (!CHANNEL_NAME_PATTERN.matcher(channelName).matches()) {
            throw new CustomException(ErrorCode.CHANNEL_NAME_INVALID);
        }
    }

    private void validateRecordingParams(String resourceId, String sid) {
        if (resourceId == null || resourceId.trim().isEmpty()) {
            throw new CustomException(ErrorCode.AGORA_RESOURCE_ID_REQUIRED);
        }
        if (sid == null || sid.trim().isEmpty()) {
            throw new CustomException(ErrorCode.AGORA_SID_REQUIRED);
        }
    }

    // ========== 비즈니스 로직 헬퍼 메서드 ==========

    public boolean isParticipant(Long userId) {
        return user1.getId().equals(userId) || user2.getId().equals(userId);
    }

    public User getPartner(Long userId) {
        if (user1.getId().equals(userId)) {
            return user2;
        } else if (user2.getId().equals(userId)) {
            return user1;
        } else {
            throw new CustomException(ErrorCode.INVALID_PARTICIPANT);
        }
    }

    public boolean isInProgress() {
        return callStatus == CallStatus.IN_PROGRESS;
    }

    public boolean isCompleted() {
        return callStatus == CallStatus.COMPLETED;
    }

    public boolean isRecordingActive() {
        return recordingStartedAt != null && recordingEndedAt == null;
    }

    public boolean hasRecordingFile() {
        return recordingFileUrl != null && !recordingFileUrl.trim().isEmpty();
    }

}
