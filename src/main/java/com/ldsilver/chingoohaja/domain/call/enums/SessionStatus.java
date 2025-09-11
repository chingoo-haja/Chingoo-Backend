package com.ldsilver.chingoohaja.domain.call.enums;

public enum SessionStatus {
    READY,      // 세션 준비됨 (토큰 발급됨)
    JOINED,     // 채널에 참가함
    LEFT,       // 채널에서 나감
    EXPIRED,    // 토큰 만료됨
    FAILED      // 연결 실패
}
