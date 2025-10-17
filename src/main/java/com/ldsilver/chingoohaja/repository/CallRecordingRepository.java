package com.ldsilver.chingoohaja.repository;

import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.CallRecording;
import com.ldsilver.chingoohaja.domain.call.enums.RecordingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CallRecordingRepository extends JpaRepository<CallRecording, Long> {
    // 통화별 녹음 파일 조회
    Optional<CallRecording> findByCall(Call call);

    // 녹음 상태별 조회
    List<CallRecording> findByRecordingStatus(RecordingStatus status);

    Optional<CallRecording> findByCallId(Long callId);

    Long call(Call call);
}
