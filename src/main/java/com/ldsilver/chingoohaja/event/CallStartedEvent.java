package com.ldsilver.chingoohaja.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CallStartedEvent {
    private final Long callId;
    private final String channelName;
}
