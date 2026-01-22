package com.ldsilver.chingoohaja.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RecordingCompletedEvent {
    private final Long callId;
    private final String filePath;
    private final Integer durationSeconds;
    private final Long fileSize;
}