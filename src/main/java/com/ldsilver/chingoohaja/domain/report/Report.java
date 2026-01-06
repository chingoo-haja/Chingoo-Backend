package com.ldsilver.chingoohaja.domain.report;

import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.common.BaseEntity;
import com.ldsilver.chingoohaja.domain.report.enums.ReportReason;
import com.ldsilver.chingoohaja.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reports",
        indexes = {
                @Index(name = "idx_reporter_id", columnList = "reporter_id"),
                @Index(name = "idx_reported_user_id", columnList = "reported_user_id"),
                @Index(name = "idx_call_id", columnList = "call_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;  // 신고자

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_user_id", nullable = false)
    private User reportedUser;  // 신고당한 사람

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "call_id", nullable = true)
    private Call call;  // 신고가 발생한 통화 (nullable)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportReason reason;

    @Column(length = 500)
    private String details;  // 상세 사유 (선택)

    public static Report of(User reporter, User reportedUser, Call call,
                            ReportReason reason, String details) {
        Report report = new Report();
        report.reporter = reporter;
        report.reportedUser = reportedUser;
        report.call = call;
        report.reason = reason;
        report.details = details;
        return report;
    }

    public static Report of(User reporter, User reportedUser,
                            ReportReason reason, String details) {
        return of(reporter, reportedUser, null, reason, details);
    }
}
