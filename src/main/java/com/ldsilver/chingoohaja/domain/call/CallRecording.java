package com.ldsilver.chingoohaja.domain.call;

import com.ldsilver.chingoohaja.domain.call.enums.RecordingStatus;
import com.ldsilver.chingoohaja.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "call_recordings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CallRecording extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "call_id", nullable = false)
    private Call call;

    @Column(length = 100)
    private String agoraResourceId;

    @Column(length = 100)
    private String agoraSid;

    @Column(nullable = false)
    private String filePath;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false)
    private String fileFormat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordingStatus recordingStatus;

    // 녹음 시간
    private LocalDateTime recordingStartedAt;

    private LocalDateTime recordingEndedAt;

    private Integer recordingDurationSeconds;

    public static CallRecording create(Call call, String resourceId, String sid) {
        CallRecording recording = new CallRecording();
        recording.call = call;
        recording.agoraResourceId = resourceId;
        recording.agoraSid = sid;
        recording.recordingStatus = RecordingStatus.PROCESSING;
        recording.recordingStartedAt = LocalDateTime.now();
        return recording;
    }

    public void complete(String filePath, Long fileSize, String fileFormat) {
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.fileFormat = fileFormat;
        this.recordingStatus = RecordingStatus.COMPLETED;
        this.recordingEndedAt = LocalDateTime.now();

        if (this.recordingStartedAt != null) {
            long seconds = java.time.Duration
                    .between(recordingStartedAt, recordingEndedAt)
                    .getSeconds();
            this.recordingDurationSeconds = (int) Math.max(0, seconds);
        }
    }

    public void fail() {
        this.recordingStatus = RecordingStatus.FAILED;
        this.recordingEndedAt = LocalDateTime.now();
    }
}
