package com.ldsilver.chingoohaja.repository;

import com.ldsilver.chingoohaja.domain.call.CallRecording;
import com.ldsilver.chingoohaja.domain.call.enums.RecordingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CallRecordingRepository extends JpaRepository<CallRecording, Long> {

    // 녹음 상태별 조회
    List<CallRecording> findByRecordingStatus(RecordingStatus status);

    Optional<CallRecording> findByCallId(Long callId);

    @Query("SELECT cr FROM CallRecording cr JOIN FETCH cr.call WHERE cr.call.id = :callId")
    Optional<CallRecording> findByCallIdWithCall(@Param("callId") Long callId);

    @Query("SELECT cr FROM CallRecording cr WHERE cr.recordingStatus = 'PROCESSING' " +
            "AND cr.recordingStartedAt < :threshold")
    List<CallRecording> findStuckProcessingRecordings(@Param("threshold") LocalDateTime threshold);
}
