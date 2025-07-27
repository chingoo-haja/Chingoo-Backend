package com.ldsilver.chingoohaja.domain.call;

import com.ldsilver.chingoohaja.domain.call.enums.RecordingStatus;
import com.ldsilver.chingoohaja.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Column(nullable = false)
    private String filePath;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false)
    private String fileFormat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordingStatus recordingStatus;

    public static CallRecording of(
            Call call,
            String filePath,
            Long fileSize,
            String fileFormat,
            RecordingStatus recordingStatus) {
        CallRecording callRecording = new CallRecording();
        callRecording.call = call;
        callRecording.filePath = filePath;
        callRecording.fileSize = fileSize;
        callRecording.fileFormat = fileFormat;
        callRecording.recordingStatus = recordingStatus;
        return callRecording;
    }

    public static CallRecording from(Call call, String filePath, Long fileSize, String fileFormat) {
        return of(call, filePath, fileSize, fileFormat, RecordingStatus.PROCESSING);
    }
}
