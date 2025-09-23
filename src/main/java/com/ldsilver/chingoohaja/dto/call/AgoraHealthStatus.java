package com.ldsilver.chingoohaja.dto.call;

import lombok.Builder;

@Builder
public record AgoraHealthStatus(
        boolean isHealthy,
        boolean tokenGenerationAvailable,
        boolean restApiAvailable,
        String statusMessage,
        String errorMessage,
        java.time.LocalDateTime checkedAt
) {
    public boolean canMakeCalls() {
        return tokenGenerationAvailable;
    }

    public boolean canUseCloudRecording() {
        return tokenGenerationAvailable && restApiAvailable;
    }

    public boolean isFullyOperational() {
        return tokenGenerationAvailable && restApiAvailable;
    }
}
