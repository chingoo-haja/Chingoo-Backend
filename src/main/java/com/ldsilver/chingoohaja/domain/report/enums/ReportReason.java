package com.ldsilver.chingoohaja.domain.report.enums;

public enum ReportReason {
    INAPPROPRIATE_LANGUAGE("부적절한 언어 사용"),
    HARASSMENT("괴롭힘"),
    SPAM("스팸/광고"),
    INAPPROPRIATE_CONTENT("부적절한 내용"),
    OFFENSIVE_BEHAVIOR("불쾌한 행동"),
    PRIVACY_VIOLATION("개인정보 침해"),
    OTHER("기타");

    private final String description;

    ReportReason(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
