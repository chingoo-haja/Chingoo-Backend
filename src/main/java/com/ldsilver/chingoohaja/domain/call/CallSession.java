package com.ldsilver.chingoohaja.domain.call;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.call.enums.SessionStatus;
import com.ldsilver.chingoohaja.domain.common.BaseEntity;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.validation.CallValidationConstants;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(name = "call_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CallSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "call_id", nullable = false)
    private Call call;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    @NotNull(message = "Agora UID는 필수입니다.")
    @Min(0)
    private Long agoraUid;

    @NotBlank(message = "RTC Token은 필수입니다.")
    @Column(nullable = false, length = 2048)
    @Size(max = 2048, message = "RTC Token은 2048자를 초과할 수 없습니다.")
    @ToString.Exclude
    private String rtcToken;

    @Column(length = 2048)
    @Size(max = 2048, message = "RTM Token은 2048자를 초과할 수 없습니다.")
    @ToString.Exclude
    private String rtmToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull
    private SessionStatus sessionStatus;

    private LocalDateTime joinedAt;

    private LocalDateTime leftAt;

    @Min(1) @Max(6)
    private Integer connectionQuality; // 1-6 (1: Excellent, 6: Down)

    @Min(0)
    private Integer audioBitrate;

    @DecimalMin(value = "0.0", inclusive = true)
    @DecimalMax(value = "100.0", inclusive = true)
    private Double packetLossRate;

    private LocalDateTime tokenExpiresAt;

    public static CallSession of(
            Call call,
            User user,
            Long agoraUid,
            String rtcToken,
            String rtmToken,
            SessionStatus sessionStatus,
            LocalDateTime tokenExpiresAt
    ) {
        validateParams(call, user, agoraUid, rtcToken);

        CallSession session = new CallSession();
        session.call = call;
        session.user = user;
        session.agoraUid = agoraUid;
        session.rtcToken = rtcToken.trim();
        session.rtmToken = (rtmToken != null && !rtmToken.trim().isEmpty()) ? rtmToken.trim() : null;
        session.sessionStatus = (sessionStatus != null) ? sessionStatus : SessionStatus.READY;
        session.tokenExpiresAt = tokenExpiresAt;
        return session;
    }

    public static CallSession from(Call call, User user, Long agoraUid, String rtcToken) {
        LocalDateTime expiredAt = LocalDateTime.now()
                .plusSeconds(CallValidationConstants.DEFAULT_TTL_SECONDS_ONE_HOURS);
        return of(call, user, agoraUid, rtcToken, null, SessionStatus.READY, expiredAt);
    }

    private static void validateParams(Call call, User user, Long agoraUid, String rtcToken) {
        if (call == null) {
            throw new CustomException(ErrorCode.CALL_REQUIRED);
        }
        if (user == null) {
            throw new CustomException(ErrorCode.USER_REQUIRED);
        }
        if (agoraUid == null || agoraUid < 0) {
            throw new CustomException(ErrorCode.AGORA_UID_INVALID);
        }
        if (rtcToken == null || rtcToken.trim().isEmpty()) {
            throw new CustomException(ErrorCode.RTC_TOKEN_REQUIRED);
        }
        Long userId = user.getId();
        if (userId == null) {
                throw new CustomException(ErrorCode.USER_REQUIRED);
        }
        if (!call.isParticipant(userId)) {
            throw new CustomException(ErrorCode.CALL_NOT_PARTICIPANT);
        }
    }

    /**
     * 세션 참가 처리
     */
    public void joinSession() {
        if (this.sessionStatus == SessionStatus.READY) {
            this.sessionStatus = SessionStatus.JOINED;
            this.joinedAt = LocalDateTime.now();
            this.leftAt = null;
        } else {
            throw new CustomException(ErrorCode.SESSION_ALREADY_JOINED);
        }
    }

    /**
     * 세션 떠나기 처리
     */
    public void leaveSession() {
        if (this.sessionStatus == SessionStatus.JOINED) {
            this.sessionStatus = SessionStatus.LEFT;
            this.leftAt = LocalDateTime.now();
        } else {
            throw new CustomException(ErrorCode.SESSION_NOT_JOINED);
        }
    }

    /**
     * 연결 품질 업데이트
     */
    public void updateConnectionQuality(int quality, int bitrate, double packetLoss) {
        if (!isActive()) {
            throw new CustomException(ErrorCode.SESSION_NOT_JOINED);
        }
        validateQualityParams(quality, bitrate, packetLoss);

        this.connectionQuality = quality;
        this.audioBitrate = bitrate;
        this.packetLossRate = packetLoss;
    }

    /**
     * 토큰 갱신
     */
    public void refreshTokens(String newRtcToken, String newRtmToken, LocalDateTime newTokenExpiresAt) {
        if (newRtcToken == null || newRtcToken.trim().isEmpty()) {
            throw new CustomException(ErrorCode.RTC_TOKEN_REQUIRED);
        }

        String rtc = newRtcToken.trim();
        this.rtcToken = rtc;
        if (newRtmToken != null && !newRtmToken.trim().isEmpty()) {
            this.rtmToken = newRtmToken.trim();
        }
        this.tokenExpiresAt = newTokenExpiresAt;
        if (this.sessionStatus == SessionStatus.EXPIRED) {
            this.sessionStatus = SessionStatus.READY;
            this.joinedAt = null;
            this.leftAt = null;
        }
    }

    private void validateQualityParams(int quality, int bitrate, double packetLoss) {
        if (quality < 1 || quality > 6) {
            throw new CustomException(ErrorCode.QUALITY_RANGE_INVALID);
        }
        if (bitrate < 0) {
            throw new CustomException(ErrorCode.BIT_RATE_TOO_SMALL);
        }
        if (packetLoss < 0.0 || packetLoss > 100.0) {
            throw new CustomException(ErrorCode.PACKET_LOSS_RANGE_INVALID);
        }
    }

    /**
     * 세션이 활성 상태인지 확인
     */
    public boolean isActive() {
        return sessionStatus == SessionStatus.JOINED;
    }

    /**
     * 세션 지속 시간 계산 (초)
     */
    public Integer getSessionDurationSeconds() {
        if (joinedAt == null) {
            return null;
        }

        LocalDateTime endTime = (leftAt != null && !leftAt.isBefore(joinedAt)) ? leftAt : LocalDateTime.now();
        long secs = java.time.Duration.between(joinedAt, endTime).getSeconds();
        return (int) Math.max(0, secs);
    }

}
