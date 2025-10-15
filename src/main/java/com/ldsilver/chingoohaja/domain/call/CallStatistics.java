package com.ldsilver.chingoohaja.domain.call;

import com.ldsilver.chingoohaja.domain.common.BaseEntity;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.dto.call.request.CallStatisticsRequest;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "call_statistics",
        uniqueConstraints = @UniqueConstraint(columnNames = {"call_id", "user_id"}),
        indexes = {
                @Index(name = "idx_call_statistics_call_id", columnList = "call_id"),
                @Index(name = "idx_call_statistics_user_id", columnList = "user_id"),
                @Index(name = "idx_call_statistics_created_at", columnList = "created_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CallStatistics extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "call_id", nullable = false)
    private Call call;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 통화 시간
    @Column(name = "duration_seconds", nullable = false)
    private Integer durationSeconds;

    // 데이터 전송량
    @Column(name = "send_bytes", nullable = false)
    private Long sendBytes;

    @Column(name = "receive_bytes", nullable = false)
    private Long receiveBytes;

    @Column(name = "total_bytes", nullable = false)
    private Long totalBytes;

    // 비트레이트
    @Column(name = "send_bitrate_kbps", nullable = false)
    private Integer sendBitrateKbps;

    @Column(name = "receive_bitrate_kbps", nullable = false)
    private Integer receiveBitrateKbps;

    // 오디오 전송량
    @Column(name = "audio_send_bytes", nullable = false)
    private Long audioSendBytes;

    @Column(name = "audio_receive_bytes", nullable = false)
    private Long audioReceiveBytes;

    // 네트워크 품질
    @Column(name = "uplink_network_quality", nullable = false)
    private Integer uplinkNetworkQuality;

    @Column(name = "downlink_network_quality", nullable = false)
    private Integer downlinkNetworkQuality;

    @Column(name = "average_network_quality", nullable = false)
    private Double averageNetworkQuality;

    @Column(name = "network_quality_description", length = 20)
    private String networkQualityDescription;

    @Builder
    private CallStatistics(Call call, User user, Integer durationSeconds,
                           Long sendBytes, Long receiveBytes,
                           Integer sendBitrateKbps, Integer receiveBitrateKbps,
                           Long audioSendBytes, Long audioReceiveBytes,
                           Integer uplinkNetworkQuality, Integer downlinkNetworkQuality,
                           Double averageNetworkQuality, String networkQualityDescription) {
        this.call = call;
        this.user = user;
        this.durationSeconds = durationSeconds;
        this.sendBytes = sendBytes;
        this.receiveBytes = receiveBytes;
        this.totalBytes = sendBytes + receiveBytes;
        this.sendBitrateKbps = sendBitrateKbps;
        this.receiveBitrateKbps = receiveBitrateKbps;
        this.audioSendBytes = audioSendBytes;
        this.audioReceiveBytes = audioReceiveBytes;
        this.uplinkNetworkQuality = uplinkNetworkQuality;
        this.downlinkNetworkQuality = downlinkNetworkQuality;
        this.averageNetworkQuality = averageNetworkQuality;
        this.networkQualityDescription = networkQualityDescription;
    }

    public static CallStatistics from(Call call, User user,
                                      CallStatisticsRequest request) {
        return CallStatistics.builder()
                .call(call)
                .user(user)
                .durationSeconds(request.duration())
                .sendBytes(request.sendBytes())
                .receiveBytes(request.receiveBytes())
                .sendBitrateKbps(request.sendBitrate())
                .receiveBitrateKbps(request.receiveBitrate())
                .audioSendBytes(request.audioSendBytes())
                .audioReceiveBytes(request.audioReceiveBytes())
                .uplinkNetworkQuality(request.uplinkNetworkQuality())
                .downlinkNetworkQuality(request.downlinkNetworkQuality())
                .averageNetworkQuality(request.getAverageNetworkQuality())
                .networkQualityDescription(request.getNetworkQualityDescription())
                .build();
    }
}
