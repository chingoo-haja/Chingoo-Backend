package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.report.Report;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.dto.report.request.ReportUserRequest;
import com.ldsilver.chingoohaja.repository.CallRepository;
import com.ldsilver.chingoohaja.repository.ReportRepository;
import com.ldsilver.chingoohaja.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final CallRepository callRepository;
    private final FriendshipService friendshipService;

    @Transactional
    public void reportUser(Long reporterId, Long reportedUserId, ReportUserRequest request) {
        if (reporterId.equals(reportedUserId)) {
            throw new CustomException(ErrorCode.SELF_REPORT_NOT_ALLOWED);
        }

        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        User reportedUser = userRepository.findById(reportedUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (reportRepository.existsByReporterAndReportedUser(reporter, reportedUser)) {
            throw new CustomException(ErrorCode.DUPLICATE_REPORT_REQUEST);
        }

        Call call = null;
        if (request.callId() != null) {
            call = callRepository.findById(request.callId()).orElse(null);
        }

        Report report = Report.of(
                reporter,
                reportedUser,
                call,
                request.reason(),
                request.details()
        );
        reportRepository.save(report);

        friendshipService.reportUser(reporterId, reportedUserId);

        log.info("사용자 신고 완료 - reporterId: {}, reportedId: {}, reason: {}",
                reporterId, reportedUserId, request.reason());
    }
}
