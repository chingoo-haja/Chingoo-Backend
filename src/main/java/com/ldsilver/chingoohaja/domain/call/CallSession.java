package com.ldsilver.chingoohaja.domain.call;

import com.ldsilver.chingoohaja.domain.call.enums.SessionStatus;
import com.ldsilver.chingoohaja.domain.common.BaseEntity;
import com.ldsilver.chingoohaja.domain.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Column(name = "agora_uid", nullable = false)
    private Integer agoraUid;

    @NotBlank(message = "RTC Token은 필수입니다.")
    @Column(name = "rtc_token", nullable = false, length = 2048)
    @Size(max = 2048, message = "RTC Token은 2048자를 초과할 수 없습니다.")
    private String rtcToken;

    @Column(name = "rtm_token", length = 2048)
    @Size(max = 2048, message = "RTM Token은 2048자를 초과할 수 없습니다.")
    private String rtmToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_status", nullable = false)
    private SessionStatus sessionStatus;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Column(name = "connection_quality")
    private Integer connectionQuality; // 1-6 (1: Excellent, 6: Down)

    @Column(name = "audio_bitrate")
    private Integer audioBitrate;

    @Column(name = "packet_loss_rate")
    private Double packetLossRate;

    public static CallSession of(
            Call call,
            User user,
            Integer agoraUid,
            String rtcToken,
            String rtmToken,
            SessionStatus sessionStatus
    ) {
        validateParams(call, user, agoraUid, rtcToken);

        CallSession session = new CallSession();
        session.call = call;
        session.user = user;
        session.agoraUid = agoraUid;
        session.rtcToken = rtcToken;
        session.rtmToken = rtmToken;
        session.sessionStatus = sessionStatus;
        return session;
    }

    public static CallSession from(Call call, User user, Integer agoraUid, String rtcToken) {
        return of(call, user, agoraUid, rtcToken, null, SessionStatus.READY);
    }

    private static void validateParams(Call call, User user, Integer agoraUid, String rtcToken) {
        if (call == null) {
            throw new IllegalArgumentException("Call은 필수입니다.");
        }
        if (user == null) {
            throw new IllegalArgumentException("User는 필수입니다.");
        }
        if (agoraUid == null) {
            throw new IllegalArgumentException("Agora UID는 필수입니다.");
        }
        if (rtcToken == null || rtcToken.trim().isEmpty()) {
            throw new IllegalArgumentException("RTC Token은 필수입니다.");
        }
        if (!call.isParticipant(user.getId())) {
            throw new IllegalArgumentException("사용자는 해당 통화의 참가자가 아닙니다.");
        }
    }

    /**
     * 세션 참가 처리
     */
    public void joinSession() {
        if (this.sessionStatus == SessionStatus.READY) {
            this.sessionStatus = SessionStatus.JOINED;
            this.joinedAt = LocalDateTime.now();
        } else {
            throw new IllegalStateException("이미 참가하였거나 참가할 수 없는 상태입니다.");
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
            throw new IllegalStateException("참가하지 않은 세션에서 나갈 수 없습니다.");
        }
    }

    /**
     * 연결 품질 업데이트
     */
    public void updateConnectionQuality(int quality, int bitrate, double packetLoss) {
        validateQualityParams(quality, bitrate, packetLoss);

        this.connectionQuality = quality;
        this.audioBitrate = bitrate;
        this.packetLossRate = packetLoss;
    }

    /**
     * 토큰 갱신
     */
    public void refreshTokens(String newRtcToken, String newRtmToken) {
        if (newRtcToken == null || newRtcToken.trim().isEmpty()) {
            throw new IllegalArgumentException("RTC Token은 필수입니다.");
        }

        this.rtcToken = newRtcToken;
        if (newRtmToken != null && !newRtmToken.trim().isEmpty()) {
            this.rtmToken = newRtmToken;
        }
    }

    private void validateQualityParams(int quality, int bitrate, double packetLoss) {
        if (quality < 1 || quality > 6) {
            throw new IllegalArgumentException("연결 품질은 1-6 범위여야 합니다.");
        }
        if (bitrate < 0) {
            throw new IllegalArgumentException("비트레이트는 0 이상이어야 합니다.");
        }
        if (packetLoss < 0.0 || packetLoss > 100.0) {
            throw new IllegalArgumentException("패킷 손실률은 0-100% 범위여야 합니다.");
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

        LocalDateTime endTime = leftAt != null ? leftAt : LocalDateTime.now();
        return (int) java.time.Duration.between(joinedAt, endTime).getSeconds();
    }

}
